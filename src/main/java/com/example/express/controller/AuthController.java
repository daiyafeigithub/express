package com.example.express.controller;

import com.example.express.aop.RequestRateLimit;
import com.example.express.common.constant.RedisKeyConstant;
import com.example.express.common.constant.SecurityConstant;
import com.example.express.common.constant.SessionKeyConstant;
import com.example.express.common.util.*;
import com.example.express.domain.ResponseResult;
import com.example.express.domain.bean.SysUser;
import com.example.express.domain.enums.*;
import com.example.express.security.validate.third.ThirdLoginAuthenticationToken;
import com.example.express.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

@Slf4j
@RestController
public class AuthController {
    @Autowired
    private AipService aipService;
    @Autowired
    private SmsService smsService;
    @Autowired
    private OAuthService oAuthService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private DataSchoolService dataSchoolService;

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${server.addr}")
    private String serverAddress;
    @Value("${project.third-login.qq.app-id}")
    private String qqAppId;
    @Value("${project.third-login.qq.app-key}")
    private String qqAppKey;
    @Value("${project.sms.interval-min}")
    private String smsIntervalMins;

    /**
     * ?????????????????????
     * @author jitwxs
     * @since 2018/5/2 0:02
     */
    @PostMapping(SecurityConstant.VALIDATE_CODE_URL_PREFIX + "/check-img")
    public ResponseResult checkVerifyCode(String code, HttpSession session) {
        if(StringUtils.isBlank(code)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }

        Object systemCode = session.getAttribute("validateCode");
        if(systemCode == null) {
            return ResponseResult.failure(ResponseErrorCodeEnum.SYSTEM_ERROR);
        }

        String validateCode = ((String)systemCode).toLowerCase();

        if(!validateCode.equals(code.toLowerCase())) {
            return ResponseResult.failure(ResponseErrorCodeEnum.VERIFY_CODE_ERROR);
        }

        return ResponseResult.success();
    }

    /**
     * ?????????????????????
     * @date 2019/4/17 22:40
     */
    @RequestRateLimit(limit = RateLimitEnum.RRLimit_1_60)
    @GetMapping(SecurityConstant.VALIDATE_CODE_URL_PREFIX + "/sms")
    public ResponseResult sendSms(String mobile, HttpSession session) {
        if(StringUtils.isBlank(mobile)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        if(!StringUtils.isValidTel(mobile)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.TEL_INVALID);
        }

        // ??????Session??????????????????????????????????????????????????????
        Object lastTimestamp = session.getAttribute(SessionKeyConstant.SMS_TIMESTAMP);
        // ????????????
        int intervalMins = Integer.parseInt(smsIntervalMins);
        if (lastTimestamp != null) {
            long waitSeconds = (System.currentTimeMillis() - Long.parseLong((String)lastTimestamp)) / 1000;
            if (waitSeconds < intervalMins * 60) {
                ResponseResult.failure(ResponseErrorCodeEnum.SMS_SEND_INTERVAL_TOO_SHORT, new Object[]{smsIntervalMins});
            }
        }

        // ???????????????
        String verifyCode = RandomUtils.number(6);
        ResponseErrorCodeEnum codeEnum = smsService.send(session, mobile, verifyCode);
        return ResponseResult.failure(codeEnum);
    }

    /**
     * QQ??????
     */
    @RequestMapping(SecurityConstant.QQ_LOGIN_URL)
    public void qqLogin(HttpServletResponse response) throws Exception {
        // QQ??????URL
        String qqCallbackUrl = serverAddress + SecurityConstant.QQ_CALLBACK_URL;
        // QQ?????????????????????
        String url = "https://graph.qq.com/oauth2.0/authorize";
        // ???????????????state?????????????????????????????????CSRF??????
        String state = oAuthService.genState();
        // ????????????response_type???client_id???state???redirect_uri
        String param = "response_type=code&" + "client_id=" + qqAppId + "&state=" + state
                + "&redirect_uri=" + qqCallbackUrl;

        // ??????QQ???????????????
        response.sendRedirect(url + "?" + param);
    }

    /**
     * QQ????????????
     * @param code ?????????
     * @param state ?????????????????????
     */
    @RequestMapping(SecurityConstant.QQ_CALLBACK_URL)
    public void qqCallback(String code, String state, HttpSession session, HttpServletResponse response) throws Exception {
        // ??????state??????????????????????????????CSRF??????
        if(!oAuthService.checkState(state)) {
            throw new Exception("State????????????");
        }

        // 2??????QQ???????????????????????????
        String url = "https://graph.qq.com/oauth2.0/token";
        // QQ??????URL
        String qqCallbackUrl = serverAddress + SecurityConstant.QQ_CALLBACK_URL;
        // ????????????grant_type???code???redirect_uri???client_id
        String param = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s&client_id=%s&client_secret=%s",
                code, qqCallbackUrl, qqAppId, qqAppKey);

        // ??????????????????????????????post??????
        // QQ????????????access token??????3??????????????????????????????????????????????????????
        String result = HttpClientUtils.sendPostRequest(url, param);

        /*
         * result?????????
         * ?????????access_token=A24B37194E89A0DDF8DDFA7EF8D3E4F8&expires_in=7776000&refresh_token=BD36DADB0FE7B910B4C8BBE1A41F6783
         */
        Map<String, String> resultMap = HttpClientUtils.params2Map(result);
        // ???????????????????????????access_token???????????????
        if(!resultMap.containsKey("access_token")) {
            throw new Exception("??????token??????");
        }
        // ??????token
        String accessToken = resultMap.get("access_token");

        // 3?????????Access Token??????????????????OpenID
        String meUrl = "https://graph.qq.com/oauth2.0/me";
        String meParams = "access_token=" + accessToken;
        String meResult = HttpClientUtils.sendGetRequest(meUrl, meParams);
        // ?????????????????????callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID"} );
        // ??????openid???openID???appId+qq???????????????????????????appId???QQ????????????????????????
        String openId = getQQOpenid(meResult);

        // ????????????
        ResponseResult result1 = sysUserService.thirdLogin(openId, ThirdLoginTypeEnum.QQ);
        if(result1.getCode() != ResponseErrorCodeEnum.SUCCESS.getCode()) {
            session.setAttribute(SecurityConstant.LAST_EXCEPTION, result1);
            response.sendRedirect(SecurityConstant.UN_AUTHENTICATION_URL);
            return;
        }

        // ????????????
        SysUser sysUser = (SysUser) result1.getData();
        ThirdLoginAuthenticationToken token = new ThirdLoginAuthenticationToken(sysUser.getId());
        Authentication authentication = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // ????????????
        response.sendRedirect(SecurityConstant.LOGIN_SUCCESS_URL);
    }

    /**
     * ??????Openid
     * @param str ?????????callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID"} );
     * @author jitwxs
     * @since 2018/5/22 21:37
     */
    private String getQQOpenid(String str) {
        // ?????????????????????
        String json = str.substring(str.indexOf("{"), str.indexOf("}") + 1);
        // ??????Map
        Map map = JsonUtils.jsonToObject(json, Map.class);
        return (String)map.get("openid");
    }

    /**
     * ????????????
     * @author jitwxs
     * @date 2019/5/3 0:47
     */
    @SuppressWarnings("Duplicates")
    @PostMapping("/auth/face-login")
    public ResponseResult faceLogin(String data) {
        String base64Prefix = "data:image/png;base64,";
        if(StringUtils.isBlank(data)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        if(data.startsWith(base64Prefix)) {
            data = data.substring(base64Prefix.length());
        }

        ResponseResult result = aipService.faceSearchByBase64(data);
        if(result.getCode() != ResponseErrorCodeEnum.SUCCESS.getCode()) {
            return result;
        }

        // ??????????????????????????????????????????????????????????????????????????????????????????????????????
        SysUser sysUser = (SysUser) result.getData();
        ThirdLoginAuthenticationToken token = new ThirdLoginAuthenticationToken(sysUser.getId());
        Authentication authentication = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return ResponseResult.success();
    }

    /**
     * ????????????
     * ???????????????session
     * ???????????????redis
     * @author jitwxs
     * @date 2019/5/3 0:23
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/auth/face-check")
    public ResponseResult faceCheck(HttpSession session, String data, @AuthenticationPrincipal SysUser sysUser) {
        String base64Prefix = "data:image/png;base64,";
        if(StringUtils.isBlank(data)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        if(data.startsWith(base64Prefix)) {
            data = data.substring(base64Prefix.length());
        }
        ResponseResult result = aipService.faceDetectByBase64(data, true);
        if(result.getCode() != ResponseErrorCodeEnum.SUCCESS.getCode()) {
            return result;
        }

        if (sysUser == null) {
            // ??????face_token???session?????????????????????
            session.setAttribute(SessionKeyConstant.REGISTER_FACE_TOKEN, result.getData());
        } else {
            try {
                // ??????face_token???redis????????????????????????????????????
                Map<String, String> resultData = (Map<String, String>) result.getData();
                String faceToken = resultData.get("face_token");

                redisTemplate.opsForHash().put(RedisKeyConstant.LAST_FACE_TOKEN, sysUser.getId(), faceToken);
            } catch (Exception e) {
                return ResponseResult.failure(ResponseErrorCodeEnum.REDIS_ERROR);
            }
        }

        return ResponseResult.success();
    }

    /**
     * ????????????
     * @param type ???????????? 1?????????????????????2?????????????????????3?????????
     * @date 2019/4/17 0:06
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/auth/register")
    public ResponseResult register(Integer type, String username, String password,
                                   String tel, String code, HttpSession session) {
        if(type == null) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        switch (type) {
            case 1:
                if(StringUtils.isAnyBlank(username, password)) {
                    return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
                }
                return sysUserService.registryByUsername(username, password);
            case 2:
                if(StringUtils.isAnyBlank(tel, code)) {
                    return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
                }
                if(!StringUtils.isValidTel(tel)) {
                    return ResponseResult.failure(ResponseErrorCodeEnum.TEL_INVALID);
                }
                return sysUserService.registryByTel(tel, code, session);
            case 3:
                // ??????face_token
                Map<String, String> map = (Map<String, String>) session.getAttribute(SessionKeyConstant.REGISTER_FACE_TOKEN);
                String faceToken = map.get("face_token");
                String gender = map.get("gender");
                if(StringUtils.isAnyBlank(faceToken, gender)) {
                    return ResponseResult.failure(ResponseErrorCodeEnum.NOT_FACE_TO_REGISTRY);
                }

                return sysUserService.registryByFace(faceToken, gender);
            default:
                return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
    }

    /**
     * ????????????
     * @author jitwxs
     * @date 2019/4/21 21:07
     */
    @PostMapping("/auth/complete-info")
    public ResponseResult completeInfo(Integer role, Integer school, Integer sex, String studentIdCard, String realName, String idCard,
                                       @AuthenticationPrincipal SysUser sysUser) {
        if(role == null || school == null || sex == null || StringUtils.isBlank(studentIdCard)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }

        // ???????????????????????????????????????
        SysRoleEnum roleEnum = SysRoleEnum.getByType(role);
        if(roleEnum != SysRoleEnum.USER && roleEnum != SysRoleEnum.COURIER) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        sysUser.setRole(roleEnum);

        // ?????? schoolId
        if(!dataSchoolService.isExist(school)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.SCHOOL_NOT_EXIST);
        }
        // ?????? studentIdCard
        if(!StringUtils.isNumeric(studentIdCard)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.STUDENT_IDCARD_NOT_NUMBER);
        }

        sysUser.setSchoolId(school);
        sysUser.setStudentIdCard(studentIdCard);

        // ????????????
        SexEnum sexEnum = SexEnum.getByType(sex);
        if(sexEnum == null) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        sysUser.setSex(sexEnum);

        // ??????????????????????????????????????????
        if(roleEnum == SysRoleEnum.COURIER) {
            if(StringUtils.isAnyBlank(realName, idCard)) {
                return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
            }
            if(!IDValidateUtils.check(idCard)) {
                return ResponseResult.failure(ResponseErrorCodeEnum.ID_CARD_INVALID);
            }
            if(StringUtils.containsSpecial(realName) || StringUtils.containsNumber(realName)) {
                return ResponseResult.failure(ResponseErrorCodeEnum.REAL_NAME_INVALID);
            }

            sysUser.setIdCard(idCard);
            sysUser.setRealName(realName);
        }

        if(!sysUserService.updateById(sysUser)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
        }

        return ResponseResult.success();
    }
}

package com.example.express.exception;

import com.example.express.common.constant.SecurityConstant;
import com.example.express.domain.ResponseResult;
import com.example.express.domain.enums.ResponseErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义异常
     */
    @ExceptionHandler(value = CustomException.class)
    public Object customException(HttpServletRequest request, CustomException e) {
        log.error("[业务异常] code: {}, msg: {}", e.getCode(), e.getMessage());
        
        if (isAjaxRequest(request)) {
            return ResponseResult.failure(e.getCode(), e.getMessage());
        }

        Map<String, Object> map = new HashMap<>(16);
        map.put("errorCode", e.getCode());
        map.put("errorMsg", e.getMessage());
        return new ModelAndView("error/error", map);
    }

    /**
     * 处理权限异常
     */
    @ExceptionHandler(value = AccessDeniedException.class)
    public void accessDeniedException(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("[权限异常] 访问受限: {}", request.getRequestURI());
        if (isAjaxRequest(request)) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\": 403, \"msg\": \"权限不足\"}");
        } else {
            response.sendRedirect(SecurityConstant.LOGIN_SUCCESS_URL);
        }
    }

    /**
     * 处理系统未知异常
     */
    @ExceptionHandler(value = Exception.class)
    public Object handleException(HttpServletRequest request, Exception e) {
        log.error("[系统异常] 路径: {}, 错误: ", request.getRequestURI(), e);

        if (isAjaxRequest(request)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.SYSTEM_ERROR);
        }

        Map<String, Object> map = new HashMap<>(16);
        map.put("errorCode", ResponseErrorCodeEnum.SYSTEM_ERROR.getCode());
        map.put("errorMsg", "系统开小差了，请稍后再试");
        return new ModelAndView("error/error", map);
    }

    /**
     * 判断是否为 AJAX 请求
     */
    private boolean isAjaxRequest(HttpServletRequest request) {
        String accept = request.getHeader("accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        return xRequestedWith != null && xRequestedWith.contains("XMLHttpRequest");
    }
}
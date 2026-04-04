package com.example.express.controller.api;

import com.example.express.aop.RequestRateLimit;
import com.example.express.common.util.StringUtils;
import com.example.express.domain.ResponseResult;
import com.example.express.domain.bean.SysUser;
import com.example.express.domain.enums.RateLimitEnum;
import com.example.express.domain.enums.ResponseErrorCodeEnum;
import com.example.express.service.OrderInfoService;
import com.example.express.service.OrderPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API支付接口
 * @author jitwxs
 * @date 2019年04月22日 23:54
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
public class PaymentApiController {
    @Autowired
    private OrderInfoService orderInfoService;
    @Autowired
    private OrderPaymentService orderPaymentService;

    /**
     * 手动同步支付状态
     * @author jitwxs
     * @date 2019/4/26 0:51
     */
    @PostMapping("/sync")
    @RequestRateLimit(limit = RateLimitEnum.RRLimit_1_10)
    public ResponseResult syncPaymentStatus(String orderId, @AuthenticationPrincipal SysUser sysUser) {
        log.info("Sync payment status, orderId: {}, user: {}", orderId, sysUser.getUsername());
        try {
            if(StringUtils.isBlank(orderId)) {
                return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
            }

            if(!orderInfoService.isUserOrder(orderId, sysUser.getId())) {
                log.warn("User {} has no permission to sync payment status for order {}", sysUser.getUsername(), orderId);
                return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
            }

            return orderPaymentService.syncPaymentInfo(orderId);
        } catch (Exception e) {
            log.error("Error syncing payment status", e);
            return ResponseResult.failure(ResponseErrorCodeEnum.SYSTEM_ERROR);
        }
    }
}

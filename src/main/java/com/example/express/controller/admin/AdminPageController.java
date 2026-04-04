package com.example.express.controller.admin;

import com.example.express.domain.bean.SysUser;
import com.example.express.domain.vo.user.UserInfoVO;
import com.example.express.service.OrderInfoService;
import com.example.express.service.SysUserService;
import com.example.express.service.UserFeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.Map;

/**
 * 管理员页面 Controller
 * @author xiangsheng.wu
 * @date 2019年05月02日 14:38
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminPageController {
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private OrderInfoService orderInfoService;
    @Autowired
    private UserFeedbackService feedbackService;

    /**
     * 管理员主页
     */
    @RequestMapping("/dashboard")
    public String showDashboardPage(@AuthenticationPrincipal SysUser sysUser, ModelMap map) {
        if (sysUser == null) {
            return "redirect:/login";
        }
        String frontName = sysUserService.getFrontName(sysUser);
        map.put("frontName", frontName);

        Map<String, Integer> data = sysUserService.getAdminDashboardData();
        if (data == null) {
            data = Collections.emptyMap();
        }

        String userDesc = "今日注册用数：" + data.getOrDefault("today", 0) +
                "，总用户数：" + data.getOrDefault("total", 0) +
                "，其中禁用用户数：" + data.getOrDefault("disEnable", 0) +
                "，冻结用户数：" + data.getOrDefault("lock", 0);
        map.put("userDesc", userDesc);

        Map<String, Integer> data1 = orderInfoService.getAdminDashboardData();
        if (data1 == null) {
            data1 = Collections.emptyMap();
        }
        String orderDesc = "今日新增订单数：" + data1.getOrDefault("today", 0) +
                "，总等待接单数：：" + data1.getOrDefault("wait", 0) +
                "，正在派送数：" + data1.getOrDefault("transport", 0);
        map.put("orderDesc", orderDesc);

        Map<String, Integer> data2 = feedbackService.getAdminDashboardData();
        if (data2 == null) {
            data2 = Collections.emptyMap();
        }
        String feedbackDesc = "今日新增反馈数：" + data2.getOrDefault("today", 0) +
                "，等待处理数：" + data2.getOrDefault("wait", 0);
        map.put("feedbackDesc", feedbackDesc);

        return "admin/dashboard";
    }

    /**
     * 订单管理页面
     */
    @RequestMapping("/order")
    public String showOrderPage(@AuthenticationPrincipal SysUser sysUser, ModelMap map) {
        map.put("frontName", sysUserService.getFrontName(sysUser));
        return "admin/order";
    }

    /**
     * 订单管理页面
     */
    @RequestMapping("/recycle")
    public String showRecyclePage(@AuthenticationPrincipal SysUser sysUser, ModelMap map) {
        map.put("frontName", sysUserService.getFrontName(sysUser));
        return "admin/recycle";
    }

    /**
     * 反馈管理页面
     */
    @RequestMapping("/feedback")
    public String showFeedbackPage(@AuthenticationPrincipal SysUser sysUser,ModelMap map) {
        map.put("frontName", sysUserService.getFrontName(sysUser));
        return "admin/feedback";
    }

    /**
     * 用户管理页面
     */
    @RequestMapping("/user")
    public String showUser(@AuthenticationPrincipal SysUser sysUser, ModelMap map) {
        map.put("frontName", sysUserService.getFrontName(sysUser));
        return "admin/user";
    }

    /**
     * 收益管理页面
     */
    @RequestMapping("/profit")
    public String showProfit(@AuthenticationPrincipal SysUser sysUser, ModelMap map) {
        map.put("frontName", sysUserService.getFrontName(sysUser));
        return "admin/profit";
    }

    /**
     * 个人中心页面
     */
    @RequestMapping("/info")
    public String showInfoPage(@AuthenticationPrincipal SysUser sysUser,ModelMap map) {
        if (sysUser == null) {
            return "redirect:/login";
        }
        map.put("frontName", sysUserService.getFrontName(sysUser));
        UserInfoVO userInfo = sysUserService.getUserInfo(sysUser.getId());
        map.put("info", userInfo);
        return "admin/info";
    }

    /**
     * 日志管理页面
     */
    @RequestMapping("/log")
    public String showLogPage(@AuthenticationPrincipal SysUser sysUser,ModelMap map) {
        map.put("frontName", sysUserService.getFrontName(sysUser));
        return "admin/log";
    }
}

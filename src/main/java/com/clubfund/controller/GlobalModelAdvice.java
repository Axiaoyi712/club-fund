package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.ClubService;
import com.clubfund.service.NotificationService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局 Model 注入：把当前登录用户放进所有页面的 Model，
 * 供顶部条欢迎语和左侧菜单（按角色显示）使用。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final NotificationService notificationService;
    private final ClubService clubService;

    public GlobalModelAdvice(NotificationService notificationService, ClubService clubService) {
        this.notificationService = notificationService;
        this.clubService = clubService;
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(HttpSession session, HttpServletRequest request) {
        if (isErrorPage(request)) {
            return 0;
        }
        User user = (User) session.getAttribute("loginUser");
        return user == null ? 0 : notificationService.unreadCount(user.getId());
    }

    @ModelAttribute("canSubmitActivity")
    public boolean canSubmitActivity(HttpSession session, HttpServletRequest request) {
        if (isErrorPage(request)) {
            return false;
        }
        User user = (User) session.getAttribute("loginUser");
        return user != null && clubService.hasLedClub(user.getId());
    }

    // 错误页不依赖业务查询，数据库异常时仍可显示提示。
    private boolean isErrorPage(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR
                || "/error".equals(request.getServletPath())
                || "/csrf-error".equals(request.getServletPath());
    }

    @ModelAttribute("user")
    public User currentUser(HttpSession session) {
        return (User) session.getAttribute("loginUser");
    }
}

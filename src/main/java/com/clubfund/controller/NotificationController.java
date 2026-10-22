package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 消息通知控制器：通知列表、标记已读。
 */
@Controller
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知列表 */
    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        model.addAttribute("notifications", notificationService.listFor(user.getId()));
        model.addAttribute("pendingTodoCount", notificationService.pendingTodoCount(user.getId()));
        return "notification_list";
    }

    /** 单条标记已读 */
    @PostMapping("/read/{id}")
    public String read(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        notificationService.markRead(id, user.getId());
        return "redirect:/notification/list";
    }

    /** 全部标记已读 */
    @PostMapping("/readAll")
    public String readAll(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        notificationService.markAllRead(user.getId());
        return "redirect:/notification/list";
    }
}

package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.ApproveService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 审批控制器：待审批列表、执行审批。
 */
@Controller
@RequestMapping("/approve")
public class ApproveController {

    private final ApproveService approveService;

    public ApproveController(ApproveService approveService) {
        this.approveService = approveService;
    }

    /** 待我审批的列表（分页） */
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "1") int page, HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        model.addAllAttributes(approveService.listPending(user, page));
        model.addAttribute("role", user.getRole());
        return "approve_list";
    }

    /** 执行审批（通过/驳回） */
    @PostMapping("/do")
    public String doApprove(@RequestParam Long activityId,
                            @RequestParam String action,
                            @RequestParam(required = false) String comment,
                            HttpSession session,
                            RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        String error = approveService.approve(activityId, action, comment, user);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            ra.addFlashAttribute("approvalError", error);
            ra.addFlashAttribute("approvalComment", comment);
            return "redirect:/activity/detail/" + activityId + "#approval-form";
        } else {
            ra.addFlashAttribute("success", action.equals("pass") ? "审批通过" : "已驳回");
        }
        return "redirect:/approve/list";
    }
}

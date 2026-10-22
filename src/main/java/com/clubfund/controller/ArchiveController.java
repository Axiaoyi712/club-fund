package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.ArchiveService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 活动归档控制器：归档操作 + 台账页面。
 */
@Controller
@RequestMapping("/archive")
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /** 归档一份获批的活动（详情页按钮触发） */
    @PostMapping("/do")
    public String doArchive(@RequestParam Long activityId, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        String error = archiveService.archive(activityId, user);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "活动已归档，进入经费执行台账");
        }
        return "redirect:/activity/detail/" + activityId;
    }

    /** 归档台账页（仅团委老师、学院领导、管理员可见） */
    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        String role = user.getRole();
        if (!("teacher".equals(role) || "leader".equals(role) || "admin".equals(role))) {
            return "redirect:/index";
        }
        List<Map<String, Object>> rows = archiveService.archiveList();
        model.addAttribute("rows", rows);
        model.addAllAttributes(archiveService.summary(rows));
        long[] t = archiveService.archiveTrend();
        model.addAttribute("archiveTrendText", trendText(t));
        model.addAttribute("archiveTrendCls", trendCls(t));
        return "archive_list";
    }

    private String trendText(long[] t) {
        if (t[1] == 0) {
            return "本月归档 " + t[0] + " 项";
        }
        double pct = (double) (t[0] - t[1]) / t[1] * 100;
        long p = Math.round(Math.abs(pct));
        return (t[0] >= t[1] ? "↑" : "↓") + p + "% vs 上月";
    }

    private String trendCls(long[] t) {
        if (t[1] == 0) {
            return "text-primary";
        }
        return t[0] >= t[1] ? "text-success" : "text-danger";
    }
}

package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * 统计报表控制器（仅管理员可访问）。
 */
@Controller
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ReportController(ReportService reportService, ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String report(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null || !"admin".equals(user.getRole())) {
            return "redirect:/index";
        }
        Map<String, Object> stats = reportService.stats();
        model.addAllAttributes(stats);
        try {
            // 序列化成 JSON 字符串，页面用 th:inline="javascript" 直接使用
            model.addAttribute("statsJson", objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            model.addAttribute("statsJson", "{}");
        }
        return "report";
    }
}

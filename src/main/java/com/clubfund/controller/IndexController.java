package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.IndexService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器：登录后跳转到这里。
 * 首页是"角色工作台"：不同角色看到不同的统计卡片和待办预览。
 */
@Controller
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    /** 打开首页工作台 */
    @GetMapping("/index")
    public String index(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        model.addAttribute("user", user);
        model.addAttribute("roleName", roleName(user.getRole()));
        model.addAllAttributes(indexService.dashboard(user));
        return "index";
    }

    /** 把角色代码转成中文名，给页面显示 */
    private String roleName(String role) {
        return switch (role) {
            case "applicant" -> "申请人";
            case "chairman"  -> "主席团";
            case "teacher"   -> "团委老师";
            case "leader"    -> "学院领导";
            case "admin"     -> "管理员";
            default          -> "未知角色";
        };
    }
}

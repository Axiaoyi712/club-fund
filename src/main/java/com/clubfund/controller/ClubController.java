package com.clubfund.controller;

import com.clubfund.entity.Club;
import com.clubfund.entity.User;
import com.clubfund.service.ClubService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 社团管理控制器：列表、新增、编辑、删除。
 */
@Controller
@RequestMapping("/club")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    /** 社团列表页 */
    @GetMapping("/list")
    public String list(Model model) {
        model.addAllAttributes(clubService.listWithLeader());
        return "club_list";
    }

    /** 打开新增社团表单页 */
    @GetMapping("/add")
    public String addPage() {
        return "club_form";
    }

    /** 提交新增社团 */
    @PostMapping("/add")
    public String doAdd(@RequestParam(required = false) String name,
                        @RequestParam(required = false) String description,
                        HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        Club club = new Club();
        club.setName(name);
        club.setDescription(description);
        String error = clubService.add(club, user);   // 新增人自动成为负责人
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("club", club);
            return "club_form";
        }
        return "redirect:/club/list";
    }

    /** 打开编辑表单页 */
    @GetMapping("/edit/{id}")
    public String editPage(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        Club club = clubService.getById(id);
        // 权限校验：不是负责人也不是管理员就拒绝
        if (club == null || !clubService.canManage(club, user)) {
            ra.addFlashAttribute("error", "你没有权限操作这个社团");
            return "redirect:/club/list";
        }
        model.addAttribute("club", club);
        return "club_form";
    }

    /** 提交编辑 */
    @PostMapping("/edit")
    public String doEdit(@RequestParam Long id,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String description,
                         HttpSession session, Model model,
                         RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        Club club = clubService.getById(id);
        if (club == null || !clubService.canManage(club, user)) {
            ra.addFlashAttribute("error", "你没有权限操作这个社团");
            return "redirect:/club/list";
        }
        club.setName(name);
        club.setDescription(description);
        String error = clubService.update(club);
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("club", club);
            return "club_form";
        }
        return "redirect:/club/list";
    }

    /** 删除社团（有关联申请时会被拒绝） */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        Club club = clubService.getById(id);
        if (club == null || !clubService.canManage(club, user)) {
            ra.addFlashAttribute("error", "你没有权限操作这个社团");
            return "redirect:/club/list";
        }
        String error = clubService.delete(id);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "社团已删除");
        }
        return "redirect:/club/list";
    }
}

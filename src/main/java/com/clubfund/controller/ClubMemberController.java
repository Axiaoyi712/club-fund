package com.clubfund.controller;

import com.clubfund.entity.Club;
import com.clubfund.entity.User;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.service.ClubMemberService;
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
 * 社团成员管理控制器：成员名单页、添加成员、移除成员。
 * 权限规则：管理员、团委老师、学院领导、社团负责人可管理成员。
 */
@Controller
@RequestMapping("/club/members")
public class ClubMemberController {

    private final ClubMemberService clubMemberService;
    private final ClubMapper clubMapper;
    private final ClubService clubService;

    public ClubMemberController(ClubMemberService clubMemberService, ClubMapper clubMapper, ClubService clubService) {
        this.clubMemberService = clubMemberService;
        this.clubMapper = clubMapper;
        this.clubService = clubService;
    }

    /** 某社团的成员名单页 */
    @GetMapping("/{clubId}")
    public String list(@PathVariable Long clubId, HttpSession session, Model model, RedirectAttributes ra) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            ra.addFlashAttribute("error", "社团不存在");
            return "redirect:/club/list";
        }
        User user = (User) session.getAttribute("loginUser");
        if (!clubService.canManage(club, user)) {
            ra.addFlashAttribute("error", "你没有权限操作这个社团");
            return "redirect:/club/list";
        }
        model.addAttribute("club", club);
        model.addAttribute("members", clubMemberService.listByClub(clubId));
        return "club_member";
    }

    /** 添加成员 */
    @PostMapping("/add")
    public String add(@RequestParam Long clubId, @RequestParam String memberName,
                      HttpSession session, RedirectAttributes ra) {
        Club club = clubMapper.selectById(clubId);
        User user = (User) session.getAttribute("loginUser");
        if (club == null || !clubService.canManage(club, user)) {
            ra.addFlashAttribute("error", "你没有权限操作这个社团");
            return "redirect:/club/list";
        }
        String error = clubMemberService.addMember(clubId, memberName);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            ra.addFlashAttribute("memberName", memberName);
        } else {
            ra.addFlashAttribute("success", "成员已添加");
        }
        return "redirect:/club/members/" + clubId;
    }

    /** 移除成员 */
    @PostMapping("/remove/{id}")
    public String remove(@PathVariable Long id, @RequestParam Long clubId,
                         HttpSession session, RedirectAttributes ra) {
        Club club = clubMapper.selectById(clubId);
        User user = (User) session.getAttribute("loginUser");
        if (club == null || !clubService.canManage(club, user)) {
            ra.addFlashAttribute("error", "你没有权限操作这个社团");
            return "redirect:/club/list";
        }
        String error = clubMemberService.removeMember(id, clubId, user);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "成员已移除");
        }
        return "redirect:/club/members/" + clubId;
    }
}

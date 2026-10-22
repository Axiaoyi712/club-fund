package com.clubfund.controller;

import com.clubfund.dto.ActivityForm;
import com.clubfund.entity.Activity;
import com.clubfund.entity.Club;
import com.clubfund.entity.BudgetItem;
import com.clubfund.entity.User;
import com.clubfund.service.ActivityService;
import com.clubfund.service.ClubService;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * 活动申请表控制器：列表、新增表单、提交、详情。
 */
@Controller
@RequestMapping("/activity")
public class ActivityController {

    private final ActivityService activityService;
    private final ClubService clubService;

    public ActivityController(ActivityService activityService, ClubService clubService) {
        this.activityService = activityService;
        this.clubService = clubService;
    }

    /** 申请列表页（分页） */
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "1") int page, HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        model.addAllAttributes(activityService.listFor(user, page));
        model.addAttribute("roleName", user.getRole());
        return "activity_list";
    }

    /** 看板视图：按审批状态分列 */
    @GetMapping("/board")
    public String board(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        model.addAllAttributes(activityService.board(user));
        model.addAttribute("roleName", user.getRole());
        return "activity_board";
    }

    /** 打开新增申请表页，仅可选择本人负责的社团。 */
    @GetMapping("/add")
    public String addPage(HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        List<Club> clubs = clubService.listLedBy(user.getId());
        if (clubs.isEmpty()) {
            ra.addFlashAttribute("error", "只有社团负责人可以提交申请，当前账号没有负责的社团");
            return "redirect:/activity/list";
        }
        model.addAttribute("clubs", clubs);
        ActivityForm form = new ActivityForm();
        form.setSubmissionToken(UUID.randomUUID().toString());
        BudgetItem item = new BudgetItem();
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setApplyAmount(BigDecimal.ZERO);
        form.getItems().add(item);
        model.addAttribute("form", form);
        return "activity_form";
    }

    /** 提交申请表 */
    @PostMapping("/add")
    public String doAdd(@ModelAttribute("form") ActivityForm form, BindingResult bindingResult,
                        HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        Activity submitted = activityService.submittedFor(form.getSubmissionToken(), user);
        if (submitted != null) return alreadySubmitted(submitted, ra);
        List<Club> clubs = clubService.listLedBy(user.getId());
        if (clubs.isEmpty()) {
            ra.addFlashAttribute("error", form.getClubId() != null && clubService.getById(form.getClubId()) == null
                    ? "所选社团不存在或已被删除，请重新选择"
                    : "只有社团负责人可以提交申请，当前账号没有负责的社团");
            return "redirect:/activity/list";
        }
        String error;
        try {
            error = bindingResult.hasErrors() ? bindingError(bindingResult) : activityService.add(form, user);
        } catch (DuplicateKeyException exception) {
            // 并发请求由数据库唯一索引兜底；另一请求提交成功后返回同一份申请。
            submitted = activityService.submittedFor(form.getSubmissionToken(), user);
            if (submitted == null) throw exception;
            return alreadySubmitted(submitted, ra);
        }
        if (error != null) {
            // 校验失败：保留有效凭证和内容，修改后可使用同一凭证重试。
            if (!activityService.validSubmissionToken(form.getSubmissionToken())) {
                form.setSubmissionToken(UUID.randomUUID().toString());
            }
            model.addAttribute("error", error);
            model.addAttribute("clubs", clubs);
            model.addAttribute("form", form);
            model.addAttribute("bindingResult", bindingResult);
            return "activity_form";
        }
        return "redirect:/activity/list";
    }

    private String alreadySubmitted(Activity activity, RedirectAttributes ra) {
        ra.addFlashAttribute("success", "该表单已提交，已打开原申请；如需新增，请重新打开申请表。");
        return "redirect:/activity/detail/" + activity.getId();
    }

    /** 回填被驳回的申请，沿用原社团。 */
    @GetMapping("/edit/{id}")
    public String editPage(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        Activity activity = activityService.getVisibleActivity(id, user);
        String error = activityService.resubmitError(activity, user);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/activity/detail/" + id;
        }
        resubmitModel(activity, activityService.formForResubmit(activity), model);
        return "activity_form";
    }

    /** 保存修改并重新提交，不产生另一份申请。 */
    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @ModelAttribute("form") ActivityForm form,
                       BindingResult bindingResult, HttpSession session, Model model, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        Activity activity = activityService.getVisibleActivity(id, user);
        String error = activityService.resubmitError(activity, user);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/activity/detail/" + id;
        }
        error = bindingResult.hasErrors() ? bindingError(bindingResult) : activityService.resubmit(id, form, user);
        if (error != null) {
            resubmitModel(activity, form, model);
            model.addAttribute("bindingResult", bindingResult);
            model.addAttribute("error", error);
            return "activity_form";
        }
        ra.addFlashAttribute("success", "修改已保存，已重新提交，等待主席团审批");
        return "redirect:/activity/detail/" + id;
    }

    private void resubmitModel(Activity activity, ActivityForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("editingActivity", activity);
        model.addAttribute("clubs", List.of(clubService.getById(activity.getClubId())));
    }

    private String bindingError(BindingResult result) {
        String field = result.getFieldError() == null ? "" : result.getFieldError().getField();
        if (field.startsWith("items[")) {
            String row = "第" + (Integer.parseInt(field.substring(6, field.indexOf(']'))) + 1) + "行预算：";
            if (field.endsWith(".quantity")) return row + "数量必须是正整数";
            if (field.endsWith(".unitPrice")) return row + "单价必须是有效金额";
            if (field.endsWith(".applyAmount")) return row + "申请金额必须是有效金额";
        }
        return switch (field) {
            case "clubId" -> "请选择有效的主办社团";
            case "startTime" -> "活动开始时间格式不正确，请重新选择";
            case "endTime" -> "活动结束时间格式不正确，请重新选择";
            default -> "输入格式不正确，请检查表单";
        };
    }

    /** 详情页（对齐学院申请表样式，含审批区） */
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        model.addAllAttributes(activityService.detail(id, user));
        model.addAttribute("expenseDateToday", LocalDate.now());
        if (!model.containsAttribute("expenseSubmissionToken")) {
            model.addAttribute("expenseSubmissionToken", UUID.randomUUID().toString());
        }
        return "activity_detail";
    }

    /** 被驳回的申请重新提交（状态复位为待审批） */
    @PostMapping("/resubmit/{id}")
    public String resubmit(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        String error = activityService.resubmit(id, user);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "已重新提交，等待主席团审批");
        }
        return "redirect:/activity/detail/" + id;
    }
}

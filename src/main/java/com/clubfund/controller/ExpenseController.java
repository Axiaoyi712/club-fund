package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.ExpenseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 支出登记控制器。
 */
@Controller
@RequestMapping("/expense")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    /** 登记一笔支出，成功后回到详情页 */
    @PostMapping("/add")
    public String add(@RequestParam Long activityId,
                      @RequestParam(required = false) String itemName,
                      @RequestParam BigDecimal amount,
                      @RequestParam(required = false) String note,
                      @RequestParam(required = false) String occurredDate,
                      @RequestParam(required = false) Long budgetItemId,
                      @RequestParam(required = false) String submissionToken,
                      HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute("loginUser");
        ExpenseService.AddResult result = expenseService.add(activityId, itemName, amount, note, occurredDate, budgetItemId, submissionToken, user);
        String error = result.error();
        if (error != null) {
            ra.addFlashAttribute("error", error);
            ra.addFlashAttribute("expenseSubmissionToken", expenseService.validSubmissionToken(submissionToken)
                    ? submissionToken : UUID.randomUUID().toString());
            ra.addFlashAttribute("expenseAmount", amount);
            ra.addFlashAttribute("expenseItemName", itemName);
            ra.addFlashAttribute("expenseBudgetItemId", budgetItemId);
            ra.addFlashAttribute("expenseNote", note);
            ra.addFlashAttribute("expenseOccurredDate", occurredDate);
        } else {
            ra.addFlashAttribute("success", result.duplicate()
                    ? "该笔支出已登记，请勿重复提交；如需新增，请使用页面中的新表单。" : "支出已登记");
            ra.addFlashAttribute("expenseApprovedWarning", result.approvedWarning());
            ra.addFlashAttribute("expenseBudgetWarning", result.budgetWarning());
        }
        return "redirect:/activity/detail/" + activityId;
    }
}

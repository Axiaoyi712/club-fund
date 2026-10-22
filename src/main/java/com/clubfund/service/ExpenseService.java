package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Activity;
import com.clubfund.entity.BudgetItem;
import com.clubfund.entity.Expense;
import com.clubfund.entity.User;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.BudgetItemMapper;
import com.clubfund.mapper.ExpenseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 支出登记业务。
 * 规则：只有"已获批"的活动才能登记支出（对齐学院流程：获批后才能采购）。
 */
@Service
public class ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final ActivityMapper activityMapper;
    private final BudgetItemMapper budgetItemMapper;

    public ExpenseService(ExpenseMapper expenseMapper, ActivityMapper activityMapper,
                          BudgetItemMapper budgetItemMapper) {
        this.expenseMapper = expenseMapper;
        this.activityMapper = activityMapper;
        this.budgetItemMapper = budgetItemMapper;
    }

    public record AddResult(String error, String approvedWarning, String budgetWarning, boolean duplicate) {
        public AddResult(String error, String approvedWarning, String budgetWarning) {
            this(error, approvedWarning, budgetWarning, false);
        }
    }

    public boolean validSubmissionToken(String token) {
        return token != null && token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /** 页面入口和实际写入共用权限：已获批、未归档，且是申请人本人。 */
    public boolean canAdd(Activity activity, User user) {
        return user.getId().equals(activity.getApplyUser())
                && "approved".equals(activity.getStatus())
                && !Integer.valueOf(1).equals(activity.getArchived());
    }

    /**
     * 登记一笔支出，返回校验结果及首次越过各额度的提醒。
     * - 从预算明细行下拉选择时（budgetItemId 非空），支出项目名自动取该行名称
     * - 也可以选"其他支出"手工填写项目名
     */
    @Transactional(rollbackFor = Exception.class)
    public AddResult add(Long activityId, String itemName, BigDecimal amount, String note, String occurredDate, Long budgetItemId, String submissionToken, User user) {
        if (!validSubmissionToken(submissionToken)) {
            return new AddResult("登记表已失效，请核对内容后重新提交", null, null);
        }
        // 先锁定活动，再读取已登记凭证，使并发重试只接受一次有效写入。
        Activity activity = activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getId, activityId).last("FOR UPDATE"));
        if (activity == null) {
            return new AddResult("申请表不存在", null, null);
        }
        if (!user.getId().equals(activity.getApplyUser())) {
            return new AddResult("只有申请人本人可以登记支出", null, null);
        }
        Expense registered = expenseMapper.selectOne(new LambdaQueryWrapper<Expense>()
                .eq(Expense::getActivityId, activityId)
                .eq(Expense::getSubmissionToken, submissionToken).last("FOR UPDATE"));
        if (registered != null) {
            return new AddResult(null, null, null, true);
        }
        if (activityId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new AddResult("请填写支出金额（金额须大于0）", null, null);
        }
        if (amount.scale() > 2) {
            return new AddResult("支出金额最多两位小数，请修改后登记", null, null);
        }
        if (amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            return new AddResult("支出金额不能超过99,999,999.99元", null, null);
        }
        if (note != null) {
            note = note.replace("\r\n", "\n").replace('\r', '\n');
            if (note.codePointCount(0, note.length()) > 200) {
                return new AddResult("备注最多200字，请修改后登记", null, null);
            }
        }
        LocalDate actualDate = null;
        if (occurredDate != null && !occurredDate.isBlank()) {
            if (!occurredDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                return new AddResult("请填写有效的实际支出日期（年-月-日），也可留空", null, null);
            }
            try {
                actualDate = LocalDate.parse(occurredDate);
            } catch (DateTimeParseException exception) {
                return new AddResult("请填写有效的实际支出日期（年-月-日），也可留空", null, null);
            }
            if (actualDate.getYear() < 1000) {
                return new AddResult("实际支出日期不能早于1000年1月1日", null, null);
            }
            if (actualDate.isAfter(LocalDate.now())) {
                return new AddResult("实际支出日期不能晚于今天", null, null);
            }
        }
        // 只有获批的活动才能登记支出
        if (!"approved".equals(activity.getStatus())) {
            return new AddResult("只有已获批的活动才能登记支出", null, null);
        }
        // 已归档的活动经费执行已闭环，不能再登记支出
        if (activity.getArchived() != null && activity.getArchived() == 1) {
            return new AddResult("该活动已归档，不能再登记支出", null, null);
        }
        // 关联预算明细行：校验归属 + 自动取项目名
        String finalItemName = itemName;
        BudgetItem item = null;
        if (budgetItemId != null) {
            item = budgetItemMapper.selectById(budgetItemId);
            if (item == null || !item.getActivityId().equals(activityId)) {
                return new AddResult("选择的预算明细行不属于这份申请", null, null);
            }
            finalItemName = item.getItemName();
        }
        if (finalItemName == null || finalItemName.isBlank()) {
            return new AddResult("请填写支出项目", null, null);
        }

        if (finalItemName.codePointCount(0, finalItemName.length()) > 100) {
            return new AddResult("支出项目名称最多100字，请修改后登记", null, null);
        }

        String approvedWarning = null;
        String budgetWarning = null;
        if (item != null) {
            BigDecimal before = expenseMapper.selectList(new LambdaQueryWrapper<Expense>()
                            .eq(Expense::getActivityId, activityId)
                            .eq(Expense::getBudgetItemId, budgetItemId))
                    .stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal after = before.add(amount.setScale(2));
            BigDecimal approved = item.getApplyAmount() == null ? BigDecimal.ZERO : item.getApplyAmount();
            if (before.compareTo(approved) <= 0 && after.compareTo(approved) > 0) {
                approvedWarning = "项目“" + finalItemName + "”累计支出已超过获批金额，超出 ¥"
                        + after.subtract(approved).toPlainString() + "。";
            }
            BigDecimal budget = item.getBudgetAmount();
            if (budget != null && before.compareTo(budget) <= 0 && after.compareTo(budget) > 0) {
                budgetWarning = "项目“" + finalItemName + "”累计支出已超过预算，超出 ¥"
                        + after.subtract(budget).toPlainString() + "。";
            }
        }

        Expense expense = new Expense();
        expense.setActivityId(activityId);
        expense.setSubmissionToken(submissionToken);
        expense.setItemName(finalItemName);
        expense.setAmount(amount.setScale(2));
        expense.setExpenseTime(LocalDateTime.now());
        expense.setOccurredDate(actualDate);
        expense.setNote(note);
        expense.setBudgetItemId(budgetItemId);
        expenseMapper.insert(expense);
        return new AddResult(null, approvedWarning, budgetWarning);
    }
}

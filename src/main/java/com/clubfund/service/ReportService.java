package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Activity;
import com.clubfund.entity.Club;
import com.clubfund.entity.Expense;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.ExpenseMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计报表业务：给管理员看经费全景数据。
 * 聚合口径：
 * - 状态分布：按审批状态统计申请单数量
 * - 社团申请金额：按主办社团汇总申请金额
 * - 总览：申请总数、获批数、申请总额、支出总额、超支活动数
 */
@Service
public class ReportService {

    private final ActivityMapper activityMapper;
    private final ClubMapper clubMapper;
    private final ExpenseMapper expenseMapper;

    public ReportService(ActivityMapper activityMapper, ClubMapper clubMapper, ExpenseMapper expenseMapper) {
        this.activityMapper = activityMapper;
        this.clubMapper = clubMapper;
        this.expenseMapper = expenseMapper;
    }

    /** 生成统计报表所需的全部数据（JSON 友好的 Map） */
    public Map<String, Object> stats() {
        List<Activity> all = activityMapper.selectList(null);
        List<Expense> allExpenses = expenseMapper.selectList(null);

        // 1. 状态分布（value 是数量）
        Map<String, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(Activity::getStatus, Collectors.counting()));

        // 2. 各社团申请金额（按社团名称汇总，报表页直接显示）
        Map<String, BigDecimal> clubAmount = new HashMap<>();
        for (Activity a : all) {
            BigDecimal apply = a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply();
            String name = clubName(a.getClubId());
            clubAmount.merge(name, apply, BigDecimal::add);
        }

        // 3. 支出合计（全部活动）
        BigDecimal totalExpense = allExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. 超支活动数：某活动支出合计 > 申请金额
        long overCount = all.stream().filter(a -> {
            BigDecimal spent = allExpenses.stream()
                    .filter(e -> e.getActivityId().equals(a.getId()))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal apply = a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply();
            return spent.compareTo(apply) > 0;
        }).count();

        // 5. 金额分级统计：小额<1000 / 中额1000-5000 / 大额>=5000（申请单数量 + 金额）
        Map<String, Long> gradeCount = new HashMap<>();
        Map<String, BigDecimal> gradeAmount = new HashMap<>();
        for (Activity a : all) {
            BigDecimal apply = a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply();
            String grade = gradeOf(apply);
            gradeCount.merge(grade, 1L, Long::sum);
            gradeAmount.merge(grade, apply, BigDecimal::add);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("statusCount", byStatus);
        result.put("clubAmount", clubAmount);
        result.put("totalApply", all.stream()
                .map(a -> a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("totalExpense", totalExpense);
        result.put("totalCount", (long) all.size());
        result.put("unknownFirstApplyCount", all.stream().filter(a -> a.getFirstApplyTime() == null).count());
        result.put("approvedCount", all.stream().filter(a -> "approved".equals(a.getStatus())).count());
        result.put("overCount", overCount);
        result.put("gradeCount", gradeCount);
        result.put("gradeAmount", gradeAmount);
        result.put("monthTrend", monthTrend());
        return result;
    }

    /** 按首次提交月份统计本月和上月申请数；首次时间未知的旧记录不参与。 */
    public long[] monthTrend() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime thisEnd = thisStart.plusMonths(1);
        LocalDateTime lastStart = thisStart.minusMonths(1);
        long cur = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .ge(Activity::getFirstApplyTime, thisStart).lt(Activity::getFirstApplyTime, thisEnd));
        long prev = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .ge(Activity::getFirstApplyTime, lastStart).lt(Activity::getFirstApplyTime, thisStart));
        return new long[]{cur, prev};
    }

    /** 金额分级：小额<1000 / 中额1000-5000 / 大额>=5000 */
    public String gradeOf(BigDecimal apply) {
        if (apply.compareTo(new BigDecimal("1000")) < 0) return "小额(<1000)";
        if (apply.compareTo(new BigDecimal("5000")) < 0) return "中额(1000-5000)";
        return "大额(>=5000)";
    }

    /** 取社团名称（报表页显示用） */
    public String clubName(Long clubId) {
        Club club = clubMapper.selectById(clubId);
        return club != null ? club.getName() : "未知社团";
    }
}

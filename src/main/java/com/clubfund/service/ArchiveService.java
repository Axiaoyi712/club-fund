package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Activity;
import com.clubfund.entity.Club;
import com.clubfund.entity.Expense;
import com.clubfund.entity.User;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.ExpenseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动归档业务：活动执行完毕后把获批的活动收入台账。
 * - 归档后不能再登记支出（经费执行闭环结束）
 * - 台账展示：获批金额、实际支出、结余/超支、归档时间
 */
@Service
public class ArchiveService {

    private final ActivityMapper activityMapper;
    private final ExpenseMapper expenseMapper;
    private final ClubMapper clubMapper;

    public ArchiveService(ActivityMapper activityMapper, ExpenseMapper expenseMapper,
                          ClubMapper clubMapper) {
        this.activityMapper = activityMapper;
        this.expenseMapper = expenseMapper;
        this.clubMapper = clubMapper;
    }

    /** 页面提示和实际写入共用归档条件，返回 null 表示允许归档。 */
    public String archiveError(Activity activity, User user) {
        if (activity == null) {
            return "申请表不存在";
        }
        if (!"approved".equals(activity.getStatus())) {
            return "只有已获批的活动才能归档";
        }
        if (Integer.valueOf(1).equals(activity.getArchived())) {
            return "该活动已归档，请勿重复操作";
        }
        String role = user.getRole();
        if (!("teacher".equals(role) || "leader".equals(role) || "admin".equals(role))) {
            return "只有团委老师、学院领导和管理员可以归档活动";
        }
        if (activity.getEndTime() == null) {
            return "活动结束时间缺失，暂不能归档，请核对活动信息";
        }
        if (activity.getEndTime().isAfter(LocalDateTime.now())) {
            return "活动尚未结束，结束后请刷新页面再归档";
        }
        return null;
    }

    /** 归档一份已结束的获批活动。返回 null 成功，返回字符串失败原因 */
    @Transactional(rollbackFor = Exception.class)
    public String archive(Long id, User user) {
        // 与支出登记锁定同一活动，等待中的请求须重新核对最新归档状态。
        Activity a = activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getId, id).last("FOR UPDATE"));
        String error = archiveError(a, user);
        if (error != null) {
            return error;
        }
        a.setArchived(1);
        a.setArchiveTime(LocalDateTime.now());
        a.setArchiveOperatorId(user.getId());
        a.setArchiveOperatorName(user.getRealName());
        activityMapper.updateById(a);
        return null;
    }

    /** 已归档活动台账：每行带社团名、实际支出合计、结余/超支 */
    public List<Map<String, Object>> archiveList() {
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getArchived, 1)
                        .orderByDesc(Activity::getArchiveTime));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Activity a : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("activity", a);
            Club club = clubMapper.selectById(a.getClubId());
            row.put("clubName", club == null ? "—" : club.getName());

            List<Expense> expenses = expenseMapper.selectList(
                    new LambdaQueryWrapper<Expense>().eq(Expense::getActivityId, a.getId()));
            BigDecimal expenseTotal = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal applyTotal = a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply();
            row.put("expenseTotal", expenseTotal);
            row.put("balance", applyTotal.subtract(expenseTotal));   // 正数=结余，负数=超支
            rows.add(row);
        }
        return rows;
    }

    /** 台账顶部统计：归档数、归档获批金额合计、结余合计 */
    public Map<String, Object> summary(List<Map<String, Object>> rows) {
        Map<String, Object> s = new HashMap<>();
        s.put("archivedCount", (long) rows.size());
        BigDecimal applySum = BigDecimal.ZERO;
        BigDecimal balanceSum = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            Activity a = (Activity) row.get("activity");
            applySum = applySum.add(a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply());
            balanceSum = balanceSum.add((BigDecimal) row.get("balance"));
        }
        s.put("applySum", applySum);
        s.put("balanceSum", balanceSum);
        return s;
    }

    /** 本月 vs 上月 归档活动数：[本月, 上月] */
    public long[] archiveTrend() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime thisEnd = thisStart.plusMonths(1);
        LocalDateTime lastStart = thisStart.minusMonths(1);
        long cur = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getArchived, 1)
                .ge(Activity::getArchiveTime, thisStart).lt(Activity::getArchiveTime, thisEnd));
        long prev = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getArchived, 1)
                .ge(Activity::getArchiveTime, lastStart).lt(Activity::getArchiveTime, thisStart));
        return new long[]{cur, prev};
    }
}

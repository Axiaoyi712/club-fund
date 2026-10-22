package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Activity;
import com.clubfund.entity.Club;
import com.clubfund.entity.User;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.ClubMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页工作台业务：按角色返回不同的统计卡片和待办预览。
 * - 申请人：我的申请统计 + 最近申请
 * - 审批人（主席团/团委老师/领导）：待我审批数 + 最近待办
 * - 管理员：全局经费统计
 * - 所有人：未读通知数 + 最近通知
 */
@Service
public class IndexService {

    private final ActivityMapper activityMapper;
    private final ClubMapper clubMapper;
    private final NotificationService notificationService;
    private final ReportService reportService;

    public IndexService(ActivityMapper activityMapper, ClubMapper clubMapper,
                        NotificationService notificationService, ReportService reportService) {
        this.activityMapper = activityMapper;
        this.clubMapper = clubMapper;
        this.notificationService = notificationService;
        this.reportService = reportService;
    }

    /** 组装首页工作台数据 */
    public Map<String, Object> dashboard(User user) {
        Map<String, Object> data = new HashMap<>();

        // 所有人：未读通知 + 最近5条通知
        data.put("unreadCount", notificationService.unreadCount(user.getId()));
        List<com.clubfund.entity.Notification> recent = notificationService.listFor(user.getId());
        data.put("recentNotifications", recent.size() > 5 ? recent.subList(0, 5) : recent);

        switch (user.getRole()) {
            case "applicant" -> {
                // 我的申请统计
                List<Activity> mine = activityMapper.selectList(
                        new LambdaQueryWrapper<Activity>()
                                .eq(Activity::getApplyUser, user.getId())
                                .orderByDesc(Activity::getApplyTime));
                BigDecimal amount = mine.stream()
                        .map(a -> a.getTotalApply() == null ? BigDecimal.ZERO : a.getTotalApply())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                data.put("myTotal", (long) mine.size());
                data.put("unknownFirstApplyCount", mine.stream().filter(a -> a.getFirstApplyTime() == null).count());
                data.put("myApproved", mine.stream().filter(a -> "approved".equals(a.getStatus())).count());
                data.put("myPending", mine.stream().filter(a -> "submitted".equals(a.getStatus())).count());
                data.put("myAmount", amount);
                data.put("myTrend", trendInfo(monthTrend(user.getId())));
                data.put("recentActivities", mine.size() > 3 ? mine.subList(0, 3) : mine);
            }
            case "chairman", "teacher", "leader" -> {
                // 待我审批数 + 最近待办
                LambdaQueryWrapper<Activity> qw = new LambdaQueryWrapper<>();
                switch (user.getRole()) {
                    case "chairman" -> qw.eq(Activity::getStatus, "submitted");
                    case "teacher" -> qw.eq(Activity::getStatus, "chairman_passed");
                    case "leader" -> qw.eq(Activity::getStatus, "teacher_passed");
                }
                data.put("pendingCount", activityMapper.selectCount(qw));

                LambdaQueryWrapper<Activity> qw2 = new LambdaQueryWrapper<>();
                switch (user.getRole()) {
                    case "chairman" -> qw2.eq(Activity::getStatus, "submitted");
                    case "teacher" -> qw2.eq(Activity::getStatus, "chairman_passed");
                    case "leader" -> qw2.eq(Activity::getStatus, "teacher_passed");
                }
                qw2.orderByAsc(Activity::getApplyTime).last("LIMIT 3");
                List<Activity> pendingRecent = activityMapper.selectList(qw2);
                data.put("pendingRecent", pendingRecent);
                Map<Long, String> clubNames = new HashMap<>();
                pendingRecent.forEach(a -> {
                    Club club = clubMapper.selectById(a.getClubId());
                    if (club != null) clubNames.put(a.getClubId(), club.getName());
                });
                data.put("clubNames", clubNames);
            }
            case "admin" -> {
                // 全局经费统计（复用报表聚合）
                data.putAll(reportService.stats());
                data.put("adminTrend", trendInfo(reportService.monthTrend()));
            }
        }
        return data;
    }

    /** 按首次提交月份统计本月和上月申请数；首次时间未知的旧记录不参与。 */
    public long[] monthTrend(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime thisEnd = thisStart.plusMonths(1);
        LocalDateTime lastStart = thisStart.minusMonths(1);
        LambdaQueryWrapper<Activity> qwCur = new LambdaQueryWrapper<Activity>()
                .ge(Activity::getFirstApplyTime, thisStart).lt(Activity::getFirstApplyTime, thisEnd);
        LambdaQueryWrapper<Activity> qwPrev = new LambdaQueryWrapper<Activity>()
                .ge(Activity::getFirstApplyTime, lastStart).lt(Activity::getFirstApplyTime, thisStart);
        if (userId != null) {
            qwCur.eq(Activity::getApplyUser, userId);
            qwPrev.eq(Activity::getApplyUser, userId);
        }
        return new long[]{activityMapper.selectCount(qwCur), activityMapper.selectCount(qwPrev)};
    }

    /** 趋势展示信息：{text, cls} */
    public Map<String, String> trendInfo(long[] t) {
        Map<String, String> m = new HashMap<>();
        if (t[1] == 0) {
            m.put("text", "本月新增 " + t[0] + " 条");
            m.put("cls", "text-primary");
        } else {
            double pct = (double) (t[0] - t[1]) / t[1] * 100;
            long p = Math.round(Math.abs(pct));
            m.put("text", (t[0] >= t[1] ? "↑" : "↓") + p + "% vs 上月");
            m.put("cls", t[0] >= t[1] ? "text-success" : "text-danger");
        }
        return m;
    }
}

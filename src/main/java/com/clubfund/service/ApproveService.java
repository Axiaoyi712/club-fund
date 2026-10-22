package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clubfund.entity.Activity;
import com.clubfund.entity.ApproveLog;
import com.clubfund.entity.Club;
import com.clubfund.entity.User;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.ApproveLogMapper;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批业务：三级审批（主席团→团委老师→学院领导）。
 *
 * 核心规则（对应学院申请表）：
 * - 主席团（chairman）只能批"已提交"的
 * - 团委老师（teacher）只能批"主席团已通过"的
 * - 学院领导（leader）只能批"团委老师已通过"的
 * - 任一级驳回 → 状态置 rejected，记录驳回原因
 * - 每一级审批都写一条 approve_log 留痕
 */
@Service
public class ApproveService {

    /** 列表每页条数 */
    private static final long PAGE_SIZE = 10;

    private final ActivityMapper activityMapper;
    private final ApproveLogMapper approveLogMapper;
    private final ClubMapper clubMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public ApproveService(ActivityMapper activityMapper, ApproveLogMapper approveLogMapper,
                          ClubMapper clubMapper, UserMapper userMapper,
                          NotificationService notificationService) {
        this.activityMapper = activityMapper;
        this.approveLogMapper = approveLogMapper;
        this.clubMapper = clubMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /**
     * 分页查询"轮到我审批"的申请表列表。
     * 每个角色只能看到当前停留在他那一级的申请。
     */
    public Map<String, Object> listPending(User user, int pageNum) {
        LambdaQueryWrapper<Activity> qw = new LambdaQueryWrapper<>();
        switch (user.getRole()) {
            case "chairman" -> qw.eq(Activity::getStatus, "submitted");
            case "teacher" -> qw.eq(Activity::getStatus, "chairman_passed");
            case "leader" -> qw.eq(Activity::getStatus, "teacher_passed");
            case "admin" -> qw.in(Activity::getStatus, "submitted", "chairman_passed", "teacher_passed");
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "你没有权限查看审批待办");
        }
        qw.orderByAsc(Activity::getApplyTime);
        Page<Activity> page = activityMapper.selectPage(new Page<>(pageNum, PAGE_SIZE), qw);

        // 拼社团名、申请人名
        Map<Long, String> clubNames = new HashMap<>();
        Map<Long, String> userNames = new HashMap<>();
        page.getRecords().forEach(a -> {
            Club club = clubMapper.selectById(a.getClubId());
            if (club != null) clubNames.put(a.getClubId(), club.getName());
            User u = userMapper.selectById(a.getApplyUser());
            if (u != null) userNames.put(a.getApplyUser(), u.getRealName());
        });

        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("clubNames", clubNames);
        result.put("userNames", userNames);
        return result;
    }

    /**
     * 执行审批。返回 null 表示成功，返回字符串表示失败原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public String approve(Long activityId, String action, String comment, User user) {
        // 锁定当前申请，后到的审批在等待后读取最新状态。
        Activity activity = activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getId, activityId).last("FOR UPDATE"));
        if (activity == null) {
            return "申请表不存在";
        }

        // 1. 校验角色与当前状态匹配（能不能批这一级）
        if (!canApprove(user, activity.getStatus())) {
            return "该申请已被处理或审批阶段已变化，请刷新后重试";
        }

        if (comment != null) {
            comment = comment.replace("\r\n", "\n").replace('\r', '\n');
        }
        if ("reject".equals(action) && (comment == null || comment.codePoints().allMatch(c ->
                Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0xFEFF))) {
            return "请填写驳回原因，便于申请人核对";
        }
        if (comment != null && comment.codePointCount(0, comment.length()) > 500) {
            return "审批意见最多500字，请修改后提交";
        }

        if (!"pass".equals(action) && !"reject".equals(action)) {
            return "未知操作";
        }
        notificationService.completeTodos(activityId);

        // 2. 通过：状态推进；驳回：状态置 rejected
        String title = activity.getTitle();
        Long applyUserId = activity.getApplyUser();
        if ("pass".equals(action)) {
            switch (activity.getStatus()) {
                case "submitted" -> {
                    activity.setStatus("chairman_passed");
                    // 下一级：团委老师收到待办
                    notificationService.notifyRole("teacher", "todo",
                            "申请待团委老师审批：" + title, activityId);
                }
                case "chairman_passed" -> {
                    activity.setStatus("teacher_passed");
                    // 下一级：学院领导收到待办
                    notificationService.notifyRole("leader", "todo",
                            "申请待学院领导审批：" + title, activityId);
                }
                case "teacher_passed" -> {
                    activity.setStatus("approved");
                    // 全部通过：申请人收到获批结果
                    notificationService.notifyUser(applyUserId, "result",
                            "你的申请已获批：" + title, activityId);
                }
            }
        } else if ("reject".equals(action)) {
            activity.setStatus("rejected");
            activity.setRejectReason(comment);
            // 驳回：申请人收到结果提醒
            notificationService.notifyUser(applyUserId, "result",
                    "你的申请被驳回：" + title, activityId);
        }
        activityMapper.updateById(activity);

        // 3. 写审批记录（留痕）
        ApproveLog log = new ApproveLog();
        log.setActivityId(activityId);
        log.setStage(stageName(user.getRole()));
        log.setAction(action);
        log.setOperatorId(user.getId());
        log.setOperatorName(user.getRealName());
        log.setComment(comment);
        log.setCreateTime(LocalDateTime.now());
        approveLogMapper.insert(log);

        return null;
    }

    /** 当前角色能不能批这个状态 */
    private boolean canApprove(User user, String status) {
        return switch (user.getRole()) {
            case "chairman" -> "submitted".equals(status);
            case "teacher"  -> "chairman_passed".equals(status);
            case "leader"   -> "teacher_passed".equals(status);
            default         -> false;   // 申请人、其他角色不能审批
        };
    }

    /** 供其他类调用：当前用户现在是否轮到审批这张表 */
    public boolean canApproveNow(Activity activity, User user) {
        return canApprove(user, activity.getStatus());
    }

    /** 角色代码 → 审批级名称 */
    private String stageName(String role) {
        return switch (role) {
            case "chairman" -> "chairman";
            case "teacher"  -> "teacher";
            case "leader"   -> "leader";
            default         -> "unknown";
        };
    }

    /** 查询某张表的审批历史 */
    public List<ApproveLog> logsOf(Long activityId) {
        return approveLogMapper.selectList(
                new LambdaQueryWrapper<ApproveLog>()
                        .eq(ApproveLog::getActivityId, activityId)
                        .orderByAsc(ApproveLog::getCreateTime));
    }
}

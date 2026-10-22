package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.clubfund.entity.Notification;
import com.clubfund.entity.User;
import com.clubfund.mapper.NotificationMapper;
import com.clubfund.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息通知业务：流程自动推送待办和结果。
 * - notifyRole：给某角色所有用户发（如提交申请→所有主席团收到待办）
 * - notifyUser：给指定用户发（如驳回→申请人收到结果）
 */
@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public NotificationService(NotificationMapper notificationMapper, UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
    }

    /** 给单个用户发通知 */
    public void notifyUser(Long userId, String type, String content, Long relatedId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        // 通知保留摘要，完整活动名称仍可从关联申请查看。
        if (content.codePointCount(0, content.length()) > 200) {
            content = content.substring(0, content.offsetByCodePoints(0, 199)) + "…";
        }
        n.setContent(content);
        n.setRelatedId(relatedId);
        n.setIsRead(0);
        n.setIsProcessed("todo".equals(type) ? 0 : 1);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    /** 给某角色的所有用户发通知（角色广播） */
    public void notifyRole(String role, String type, String content, Long relatedId) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, role));
        users.forEach(u -> notifyUser(u.getId(), type, content, relatedId));
    }

    /** 查某用户的通知（新的在前） */
    public List<Notification> listFor(Long userId) {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));
    }

    /** 未读数量（首页徽标用） */
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    /** 当前仍需处理的待办数量，不受已读状态影响。 */
    public long pendingTodoCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, "todo")
                .eq(Notification::getIsProcessed, 0));
    }

    /** 在审批事务中先结束旧待办，再发送下一阶段或重提的新待办。 */
    public void completeTodos(Long activityId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getRelatedId, activityId)
                .eq(Notification::getType, "todo")
                .eq(Notification::getIsProcessed, 0)
                .set(Notification::getIsProcessed, 1));
    }

    /** 只更新已读字段，避免并发时覆盖审批刚写入的处理状态。 */
    public void markRead(Long id, Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
    }

    /** 全部标记已读，处理状态保持原样。 */
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}

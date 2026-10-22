package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知实体：对应 notification 表。
 * 系统自动推送两类消息：
 * - todo：审批待办（发给下一级审批人）
 * - result：审批结果（发给申请人）
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;         // 接收人id

    private String type;         // todo待办 / result结果

    private String content;      // 通知内容

    private Long relatedId;      // 关联申请id

    private Integer isRead;      // 0未读 1已读

    private Integer isProcessed; // 待办处理状态：0待处理 1已处理，与是否已读独立

    private LocalDateTime createTime;  // 通知时间
}

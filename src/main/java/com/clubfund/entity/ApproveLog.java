package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批记录实体：对应 approve_log 表。
 * 每一级审批（主席团/团委老师/学院领导）都留一条记录，可追溯。
 */
@Data
@TableName("approve_log")
public class ApproveLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;      // 属于哪份申请表

    private String stage;         // 审批级：chairman / teacher / leader

    private String action;        // 动作：pass 通过 / reject 驳回

    private Long operatorId;      // 审批人id

    private String operatorName;  // 审批人姓名

    private String comment;       // 审批意见

    private LocalDateTime createTime;  // 审批时间
}

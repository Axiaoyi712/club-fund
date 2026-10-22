package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动申请表实体：对应数据库 activity 表。
 * 对应学院《活动经费申请表》的主表部分。
 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String submissionToken; // 与申请人组成唯一提交凭证，历史记录可为空

    private Long clubId;            // 主办社团

    private String organizer;       // 承办/协办单位

    private String title;           // 活动内容（全称）

    private String location;        // 活动地点

    private LocalDateTime startTime; // 活动开始时间

    private LocalDateTime endTime;   // 活动结束时间

    private BigDecimal totalBudget;  // 预算合计（明细自动累加）

    private BigDecimal totalApply;   // 申请金额合计（明细自动累加）

    private String status;           // 状态：submitted/.../approved/rejected

    private Long applyUser;          // 申请人（=活动负责人）

    private LocalDateTime firstApplyTime; // 首次提交时间；旧记录无法确定时为空

    private LocalDateTime applyTime; // 最新提交时间，审批待办按此排序

    private String rejectReason;     // 驳回原因

    private Integer archived;        // 是否已归档：0未归档 1已归档

    private LocalDateTime archiveTime; // 归档时间

    private Long archiveOperatorId;    // 归档操作人账号，历史未知时为空

    private String archiveOperatorName; // 归档时的姓名快照，历史未知时为空
}

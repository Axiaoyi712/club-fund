package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支出登记实体：对应 expense 表。
 * 活动获批后才能登记支出，逐笔记录实际花了多少钱。
 */
@Data
@TableName("expense")
public class Expense {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String submissionToken; // 同一活动内唯一的登记凭证，历史记录可为空

    private Long activityId;        // 属于哪份申请表

    private String itemName;        // 支出项目

    private BigDecimal amount;      // 支出金额

    private LocalDateTime expenseTime;  // 系统登记时间

    private LocalDate occurredDate;     // 实际支出日期，可不填

    private String note;            // 备注

    private Long budgetItemId;      // 关联预算明细行id（明细级执行对比用）
}

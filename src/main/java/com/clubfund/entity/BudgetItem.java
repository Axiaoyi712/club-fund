package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算明细实体：对应数据库 budget_item 表。
 * 一行是一条开支：开支项目 + 数量 + 单价 + 申请金额。
 * 预算金额 = 数量 × 单价，由后端自动计算。
 */
@Data
@TableName("budget_item")
public class BudgetItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;       // 属于哪份申请表

    private String itemName;       // 开支项目

    private Integer quantity;      // 数量

    private BigDecimal unitPrice;  // 单价

    private BigDecimal budgetAmount; // 预算金额（自动算）

    private BigDecimal applyAmount;  // 申请金额
}

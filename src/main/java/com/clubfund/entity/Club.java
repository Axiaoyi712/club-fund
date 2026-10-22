package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团实体类：对应数据库 club 表。
 * 社团就是"主办单位"，申请活动时选择是哪个社团主办的。
 */
@Data
@TableName("club")
public class Club {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;          // 社团名称（主办单位）

    private String description;   // 社团简介

    private Long leaderId;        // 负责人（user表的id）

    private LocalDateTime createTime;  // 创建时间
}

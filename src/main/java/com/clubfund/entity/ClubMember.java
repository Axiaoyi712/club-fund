package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团成员实体：对应 club_member 表。
 * 记录社团成员名单，支持成员管理（一个社团可有多名成员）。
 */
@Data
@TableName("club_member")
public class ClubMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clubId;        // 所属社团

    private String memberName;  // 成员姓名

    private LocalDateTime joinTime;  // 加入时间
}

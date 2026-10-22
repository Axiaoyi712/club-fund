package com.clubfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类：对应数据库里的 user 表，一个字段对应一列。
 * @Data 是 Lombok 的注解，自动生成 getter/setter，不用自己写
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)   // 主键自增
    private Long id;

    private String username;       // 登录名

    private String password;       // 密码（BCrypt加密后的密文）

    private String realName;       // 真实姓名

    private String role;           // 角色：applicant/chairman/teacher/leader/admin

    private String phone;          // 联系方式

    private String email;          // 邮箱

    private LocalDateTime createTime;  // 注册时间
}

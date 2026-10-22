package com.clubfund.config;

import com.clubfund.entity.User;
import com.clubfund.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据初始化器：项目启动后自动运行一次。
 * 如果 user 表是空的，就插入 5 个测试账号（每种角色一个），方便直接登录测试。
 * 密码统一是 123456（BCrypt加密后存入）。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    public DataInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(String... args) {
        // 表里已经有用户就不重复插入
        if (userMapper.selectCount(null) > 0) {
            return;
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String pwd = encoder.encode("123456");   // 所有测试账号密码都是 123456

        insertUser("applicant1", "张三", "applicant", pwd);
        insertUser("chairman1", "李四", "chairman", pwd);
        insertUser("teacher1", "赵相洲", "teacher", pwd);
        insertUser("leader1", "薛彬", "leader", pwd);
        insertUser("admin1", "管理员", "admin", pwd);

        System.out.println("【初始化】已插入5个测试账号：applicant1/chairman1/teacher1/leader1/admin1，密码都是123456");
    }

    private void insertUser(String username, String realName, String role, String pwd) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setRealName(realName);
        u.setRole(role);
        u.setCreateTime(LocalDateTime.now());
        userMapper.insert(u);
    }
}

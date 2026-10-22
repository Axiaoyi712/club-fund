package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.User;
import com.clubfund.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 用户业务逻辑：登录、注册都在这写。
 * Service 层是"中间人"——Controller 调它，它调 Mapper 操作数据库。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 登录校验：按用户名查用户，再用 BCrypt 比对密码。
     * 返回 null 表示登录失败；返回 User 表示登录成功。
     */
    public User login(String username, String rawPassword) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return null;   // 用户名不存在
        }
        // 数据库存的是密文，用户输入的是明文，用 matches 比对
        if (encoder.matches(rawPassword, user.getPassword())) {
            return user;
        }
        return null;       // 密码错误
    }

    /**
     * 注册：先查重，再加密存库。新注册用户默认角色是 applicant（申请人）。
     * 返回 null 表示注册成功；否则返回具体错误提示。
     */
    public String register(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            return "请输入用户名";
        }
        if (user.getUsername().length() > 50) {
            return "用户名不能超过50个字符";
        }
        if (user.getRealName() == null || user.getRealName().isBlank()) {
            return "请输入真实姓名";
        }
        if (user.getRealName().length() > 50) {
            return "真实姓名不能超过50个字符";
        }
        String password = user.getPassword();
        if (password == null || password.isBlank()) {
            return "请输入密码";
        }
        if (password.codePointCount(0, password.length()) < 8) {
            return "密码至少需要8个字符";
        }
        // BCrypt 最多处理 72 字节；提前校验，避免超出部分被截断。
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "密码不能超过72字节，中文通常占3字节，请缩短后重试";
        }
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (exist != null) {
            return "用户名已被占用";
        }
        user.setPassword(encoder.encode(user.getPassword()));  // 明文加密后入库
        user.setRole("applicant");                             // 默认角色
        user.setCreateTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            return "用户名已被占用";  // 同名注册可能同时通过前面的查询
        }
        return null;
    }
}

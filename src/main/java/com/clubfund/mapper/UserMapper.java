package com.clubfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clubfund.entity.User;

/**
 * 用户 Mapper：MyBatis-Plus 的 BaseMapper 已经自带
 * 增删改查方法（insert/selectById/selectList...），
 * 我们不需要写 SQL，直接继承就行。
 */
public interface UserMapper extends BaseMapper<User> {
}

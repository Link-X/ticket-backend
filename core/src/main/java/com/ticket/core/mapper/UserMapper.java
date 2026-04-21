package com.ticket.core.mapper;

import com.ticket.core.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface UserMapper {

    /**
     * 插入用户
     */
    int insert(User user);

    /**
     * 根据 ID 查询用户
     */
    User selectById(Long id);

    /**
     * 根据用户名查询用户
     */
    User selectByUsername(String username);

    /**
     * 根据手机号查询用户
     */
    User selectByPhone(String phone);
}

package com.ticket.core.mapper;

import com.ticket.core.domain.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户角色 Mapper 接口
 */
@Mapper
public interface UserRoleMapper {

    /**
     * 插入用户角色
     */
    int insert(UserRole userRole);

    /**
     * 根据用户 ID 查询角色列表
     */
    List<UserRole> selectByUserId(Long userId);
}

package com.ticket.core.domain.entity;

import lombok.Data;

/**
 * 用户角色实体类
 */
@Data
public class UserRole {
    private Long id;
    private Long userId;
    private String role;
}

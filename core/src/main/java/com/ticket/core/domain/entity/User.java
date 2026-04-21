package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
public class User {
    private Long id;
    private String username;
    private String phone;
    private String email;
    private String passwordHash;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

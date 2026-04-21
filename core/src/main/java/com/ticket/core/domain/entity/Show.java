package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演出实体类
 */
@Data
public class Show {
    private Long id;
    private String name;
    private String description;
    private String category;
    private String posterUrl;
    private String venue;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

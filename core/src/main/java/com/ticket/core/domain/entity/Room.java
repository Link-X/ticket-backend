package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Room {
    private Long id;
    private String name;
    private String venue;
    private Integer rowCount;
    private Integer colCount;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

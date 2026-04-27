package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演出场次实体类
 */
@Data
public class ShowSession {
    private Long id;
    private Long showId;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalSeats;
    private Integer limitPerUser;
    private Integer status;
    /** 座位网格总行数 */
    private Integer rowCount;
    /** 座位网格总列数 */
    private Integer colCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

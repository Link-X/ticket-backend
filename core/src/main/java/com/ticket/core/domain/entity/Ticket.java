package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 票券实体类
 */
@Data
public class Ticket {
    private Long id;
    private Long orderId;
    private Long userId;
    private String qrCode;
    private String ticketNo;
    private Integer status;
    private LocalDateTime verifyTime;
}

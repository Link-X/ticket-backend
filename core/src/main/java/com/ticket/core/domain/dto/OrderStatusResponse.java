package com.ticket.core.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态响应 DTO
 */
@Data
public class OrderStatusResponse {

    @Data
    public static class TicketInfo {
        private String ticketNo;
        private String qrCode;
        private Integer status;
        private LocalDateTime verifyTime;
    }
    private Long orderId;
    private String orderNo;
    private Integer status;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private List<String> seatInfos;
    private List<TicketInfo> tickets;

    private String showName;
    private String showVenue;
    private String sessionName;
    private LocalDateTime sessionStartTime;
}

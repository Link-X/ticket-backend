package com.ticket.core.domain.dto;

import lombok.Data;
import java.util.List;

/**
 * 订单创建请求 DTO
 */
@Data
public class OrderCreateRequest {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 座位ID列表
     */
    private List<Long> seatIds;
}

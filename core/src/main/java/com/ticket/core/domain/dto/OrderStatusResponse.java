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

    /**
     * 票券信息
     */
    @Data
    public static class TicketInfo {
        /** 票券编号 */
        private String ticketNo;
        /** 入场二维码（核销时扫描） */
        private String qrCode;
        /**
         * 票券状态
         * 0 - 未使用
         * 1 - 已核销
         * 2 - 已作废（退款后失效）
         */
        private Integer status;
        /** 核销时间，status=1 时有值 */
        private LocalDateTime verifyTime;
    }

    /** 订单 ID */
    private Long orderId;
    /** 订单号（雪花算法生成，对外展示用） */
    private String orderNo;
    /**
     * 订单状态
     * 0 - 待支付（创建后 5 分钟内有效，超时自动取消）
     * 1 - 已支付
     * 2 - 已取消（未支付主动取消 或 超时自动取消）
     * 3 - 退款中（已支付订单发起取消，等待退款处理）
     * 4 - 已退款
     */
    private Integer status;
    /** 订单总金额 */
    private BigDecimal totalAmount;
    /** 订单创建时间 */
    private LocalDateTime createTime;
    /** 支付时间，status >= 1 时有值 */
    private LocalDateTime payTime;
    /** 订单过期时间，status=0 时有效，前端可据此展示支付倒计时 */
    private LocalDateTime expireTime;
    /** 座位信息列表，如 ["1排01座", "1排02座"] */
    private List<String> seatInfos;
    /** 票券列表，支付成功后异步生成，status=1 时可用 */
    private List<TicketInfo> tickets;

    /** 演出名称 */
    private String showName;
    /** 演出场馆 */
    private String showVenue;
    /** 场次名称 */
    private String sessionName;
    /** 场次开始时间 */
    private LocalDateTime sessionStartTime;
}

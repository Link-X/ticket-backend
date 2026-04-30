package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.common.util.TicketNoGenerator;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 票券服务 — 负责为订单项生成对应的票券
 */
@Service
public class TicketService {

    private final OrderItemMapper orderItemMapper;
    private final TicketMapper ticketMapper;
    /** 雪花算法 ID 生成器，workerId=1，dataCenterId=2 */
    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 2);

    public TicketService(OrderItemMapper orderItemMapper,
                         TicketMapper ticketMapper) {
        this.orderItemMapper = orderItemMapper;
        this.ticketMapper = ticketMapper;
    }

    /**
     * 为订单生成票券
     *
     * @param orderId 订单 ID
     * @param userId 用户 ID
     * @return 生成的票券列表
     */
    public List<Ticket> generateTicketsForOrder(Long orderId, Long userId) {
        // 1. 查询订单项
        List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);

        // 2. 为每个订单项生成对应的票券
        List<Ticket> tickets = new ArrayList<>();
        for (OrderItem item : orderItems) {
            Ticket ticket = new Ticket();
            ticket.setId(snowflake.nextId());
            ticket.setSeatId(item.getSeatId());
            ticket.setQrCode(UUID.randomUUID().toString());

            // 生成唯一的票号
            String ticketNo;
            int retryCount = 0;
            final int MAX_RETRIES = 100;
            do {
                if (retryCount >= MAX_RETRIES) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成票号失败：达到最大重试次数");
                }
                ticketNo = TicketNoGenerator.generate();
                retryCount++;
            } while (ticketMapper.selectByTicketNo(ticketNo) != null);

            ticket.setTicketNo(ticketNo);
            ticket.setOrderId(orderId);
            ticket.setUserId(userId);
            ticket.setStatus(0);

            LocalDateTime now = LocalDateTime.now();
            ticket.setCreateTime(now);
            ticket.setUpdateTime(now);

            tickets.add(ticket);
        }

        // 3. 批量插入票券
        ticketMapper.batchInsert(tickets);

        return tickets;
    }
}

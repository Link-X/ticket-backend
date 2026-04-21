package com.ticket.core.service;

import com.ticket.core.domain.entity.Order;
import com.ticket.core.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时服务 — 定时任务自动取消过期的待支付订单
 */
@Slf4j
@Service
public class OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderTimeoutService(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    /**
     * 定时任务：每隔 30 秒扫描一次过期订单，批量取消
     * 每次最多处理 500 条记录，避免一次性处理数据量过大
     */
    @Scheduled(fixedRate = 30000)
    public void cancelExpiredOrders() {
        List<Order> expiredOrders = orderMapper.selectExpiredOrders(0, LocalDateTime.now(), 500);

        for (Order order : expiredOrders) {
            try {
                orderService.cancelOrder(order.getId());
            } catch (Exception e) {
                log.warn("订单超时取消失败，orderId: {}, 错误信息: {}", order.getId(), e.getMessage(), e);
            }
        }
    }
}

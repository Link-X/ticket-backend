package com.ticket.core.mq.consumer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.PaymentSuccessEvent;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.SeatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InventorySyncConsumer {

    private final OrderItemMapper orderItemMapper;
    private final SeatMapper seatMapper;

    public InventorySyncConsumer(OrderItemMapper orderItemMapper, SeatMapper seatMapper) {
        this.orderItemMapper = orderItemMapper;
        this.seatMapper = seatMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.INVENTORY_SYNC_QUEUE)
    public void syncInventory(PaymentSuccessEvent event) {
        log.info("同步座位库存，orderNo={}", event.getOrderNo());
        try {
            List<Long> seatIds = orderItemMapper.selectByOrderId(event.getOrderId())
                    .stream()
                    .map(item -> item.getSeatId())
                    .collect(Collectors.toList());

            if (!seatIds.isEmpty()) {
                seatMapper.batchUpdateStatus(seatIds, 2); // 2=已售
            }
        } catch (Exception e) {
            log.error("座位库存同步失败，orderNo={}", event.getOrderNo(), e);
            throw e;
        }
    }
}

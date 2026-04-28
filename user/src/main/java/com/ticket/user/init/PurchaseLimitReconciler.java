package com.ticket.user.init;

import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.service.PurchaseLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 服务启动时对账 Redis 限购计数：
 * 从 MySQL 查出所有有效订单（待支付/已支付）的真实座位持有数，
 * 覆盖写入 Redis，消除因服务崩溃导致的计数偏差。
 */
@Slf4j
@Component
public class PurchaseLimitReconciler implements ApplicationRunner {

    private final OrderItemMapper orderItemMapper;
    private final PurchaseLimitService purchaseLimitService;

    public PurchaseLimitReconciler(OrderItemMapper orderItemMapper,
                                   PurchaseLimitService purchaseLimitService) {
        this.orderItemMapper = orderItemMapper;
        this.purchaseLimitService = purchaseLimitService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Map<String, Object>> rows = orderItemMapper.selectActiveSeatCounts();
            if (rows.isEmpty()) {
                log.info("限购对账：无活跃订单，跳过");
                return;
            }
            for (Map<String, Object> row : rows) {
                long sessionId = toLong(row.get("sessionId"));
                long userId    = toLong(row.get("userId"));
                int  seatCount = toInt(row.get("seatCount"));
                purchaseLimitService.resetCount(sessionId, userId, seatCount);
            }
            log.info("限购对账完成，共修正 {} 条记录", rows.size());
        } catch (Exception e) {
            // 对账失败不阻断启动，仅记录告警
            log.error("限购对账失败，服务正常启动但限购计数可能偏差: {}", e.getMessage(), e);
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(String.valueOf(val));
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(String.valueOf(val));
    }
}

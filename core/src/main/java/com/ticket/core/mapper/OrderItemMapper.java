package com.ticket.core.mapper;

import com.ticket.core.domain.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 订单项 Mapper 接口
 */
@Mapper
public interface OrderItemMapper {

    /**
     * 插入订单项（自动生成主键）
     */
    int insert(OrderItem orderItem);

    /**
     * 批量插入订单项
     */
    int batchInsert(@Param("items") List<OrderItem> items);

    /**
     * 根据订单 ID 查询订单项列表
     */
    List<OrderItem> selectByOrderId(Long orderId);

    /**
     * 统计有效订单中指定座位的数量（status != 2，即非取消状态）
     */
    int countBySeatIdAndValidOrder(Long seatId);

    /**
     * 按 (sessionId, userId) 聚合有效订单座位数，用于启动时对账 Redis 限购计数
     * 返回字段：session_id, user_id, seat_count
     */
    List<Map<String, Object>> selectActiveSeatCounts();
}

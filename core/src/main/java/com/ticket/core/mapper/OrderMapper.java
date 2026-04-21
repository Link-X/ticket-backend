package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper 接口
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单（自动生成主键）
     */
    int insert(Order order);

    /**
     * 根据 ID 查询订单
     */
    Order selectById(Long id);

    /**
     * 根据订单号查询订单
     */
    Order selectByOrderNo(String orderNo);

    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 查询已过期待支付订单（status=0 且 expire_time < expireTime）
     */
    List<Order> selectExpiredOrders(@Param("status") Integer status,
                                    @Param("expireTime") LocalDateTime expireTime,
                                    @Param("limit") Integer limit);

    /**
     * 更新订单状态和支付时间
     */
    int updateStatusAndPayTime(@Param("id") Long id,
                               @Param("status") Integer status,
                               @Param("payTime") LocalDateTime payTime);
}

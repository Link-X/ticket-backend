package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 票券 Mapper 接口
 */
@Mapper
public interface TicketMapper {

    /**
     * 插入票券
     */
    int insert(Ticket ticket);

    /**
     * 批量插入票券
     */
    int batchInsert(@Param("tickets") List<Ticket> tickets);

    /**
     * 根据二维码查询票券
     */
    Ticket selectByQrCode(String qrCode);

    /**
     * 根据票券编号查询票券
     */
    Ticket selectByTicketNo(String ticketNo);

    /**
     * 根据 ID 查询票券
     */
    Ticket selectById(Long id);

    /**
     * 根据订单 ID 查询票券列表
     */
    List<Ticket> selectByOrderId(Long orderId);

    /**
     * 更新票券状态和核销时间
     */
    int updateStatusAndVerifyTime(@Param("id") Long id,
                                  @Param("status") Integer status,
                                  @Param("verifyTime") LocalDateTime verifyTime);

    /**
     * 按订单 ID 批量作废全部票券
     */
    int invalidateByOrderId(@Param("orderId") Long orderId);

    /**
     * 按座位 ID 列表作废指定票券（部分退款场景）
     */
    int invalidateBySeatIds(@Param("orderId") Long orderId,
                            @Param("seatIds") List<Long> seatIds);
}

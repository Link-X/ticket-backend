package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付 Mapper 接口
 */
@Mapper
public interface PaymentMapper {

    /**
     * 插入支付记录（自动生成主键）
     */
    int insert(Payment payment);

    /**
     * 根据 ID 查询支付记录
     */
    Payment selectById(Long id);

    /**
     * 根据支付单号查询支付记录
     */
    Payment selectByPaymentNo(String paymentNo);

    /**
     * 更新支付状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}

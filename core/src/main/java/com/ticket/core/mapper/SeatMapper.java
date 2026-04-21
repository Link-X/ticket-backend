package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位 Mapper 接口
 */
@Mapper
public interface SeatMapper {

    /**
     * 插入座位（自动生成主键）
     */
    int insert(Seat seat);

    /**
     * 批量插入座位
     */
    int batchInsert(@Param("seats") List<Seat> seats);

    /**
     * 根据 ID 查询座位
     */
    Seat selectById(Long id);

    /**
     * 根据场次 ID 查询座位列表
     */
    List<Seat> selectBySessionId(Long sessionId);

    /**
     * 批量更新座位状态
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);
}

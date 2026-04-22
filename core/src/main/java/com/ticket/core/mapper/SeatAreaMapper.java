package com.ticket.core.mapper;

import com.ticket.core.domain.entity.SeatArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位区域 Mapper 接口
 */
@Mapper
public interface SeatAreaMapper {

    /**
     * 批量插入座位区域
     */
    int batchInsert(@Param("areas") List<SeatArea> areas);

    /**
     * 根据场次 ID 查询座位区域列表
     */
    List<SeatArea> selectBySessionId(Long sessionId);

    /**
     * 根据场次 ID 删除座位区域
     */
    int deleteBySessionId(Long sessionId);
}

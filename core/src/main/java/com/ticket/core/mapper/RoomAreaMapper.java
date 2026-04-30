package com.ticket.core.mapper;

import com.ticket.core.domain.entity.RoomArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoomAreaMapper {
    int batchInsert(@Param("areas") List<RoomArea> areas);
    List<RoomArea> selectByRoomId(Long roomId);
    int deleteByRoomId(Long roomId);
}

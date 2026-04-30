package com.ticket.core.mapper;

import com.ticket.core.domain.entity.RoomSeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoomSeatMapper {
    int batchInsert(@Param("seats") List<RoomSeat> seats);
    List<RoomSeat> selectByRoomId(Long roomId);
    int deleteByRoomId(Long roomId);
}

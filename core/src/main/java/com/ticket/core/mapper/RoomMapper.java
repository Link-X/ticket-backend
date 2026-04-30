package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Room;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoomMapper {
    int insert(Room room);
    int update(Room room);
    Room selectById(Long id);
    List<Room> selectAll();
}

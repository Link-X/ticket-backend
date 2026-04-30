package com.ticket.admin.controller;

import com.ticket.admin.dto.RoomAreaSaveRequest;
import com.ticket.admin.dto.RoomSeatBatchRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Room;
import com.ticket.core.service.RoomService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin/room")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/create")
    public Result<Room> createRoom(@RequestBody Room room) {
        return Result.success(roomService.createRoom(room));
    }

    @PutMapping("/update")
    public Result<Room> updateRoom(@RequestBody Room room) {
        return Result.success(roomService.updateRoom(room));
    }

    @GetMapping("/{id}")
    public Result<Room> getRoom(@PathVariable Long id) {
        return Result.success(roomService.getRoom(id));
    }

    @GetMapping("/list")
    public Result<?> listRooms() {
        return Result.success(roomService.listRooms());
    }

    /** 批量保存座位模板（覆盖写） */
    @PostMapping("/seat/batch")
    public Result<?> saveSeats(@Valid @RequestBody RoomSeatBatchRequest req) {
        roomService.saveSeats(req.getRoomId(), req.getSeats());
        return Result.success("座位模板保存成功，共 " + req.getSeats().size() + " 个");
    }

    @GetMapping("/seat/list")
    public Result<?> listSeats(@RequestParam Long roomId) {
        return Result.success(roomService.listSeats(roomId));
    }

    /** 保存默认价格区域（覆盖写） */
    @PostMapping("/area/save")
    public Result<?> saveAreas(@Valid @RequestBody RoomAreaSaveRequest req) {
        roomService.saveAreas(req.getRoomId(), req.getAreas());
        return Result.success("价格区域保存成功");
    }

    @GetMapping("/area/list")
    public Result<?> listAreas(@RequestParam Long roomId) {
        return Result.success(roomService.listAreas(roomId));
    }
}

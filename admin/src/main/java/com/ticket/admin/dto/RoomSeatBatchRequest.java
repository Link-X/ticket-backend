package com.ticket.admin.dto;

import com.ticket.core.domain.entity.RoomSeat;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class RoomSeatBatchRequest {
    @NotNull(message = "场地ID不能为空")
    private Long roomId;
    @NotEmpty(message = "座位列表不能为空")
    private List<RoomSeat> seats;
}

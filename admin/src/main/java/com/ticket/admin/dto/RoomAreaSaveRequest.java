package com.ticket.admin.dto;

import com.ticket.core.domain.entity.RoomArea;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class RoomAreaSaveRequest {
    @NotNull(message = "场地ID不能为空")
    private Long roomId;
    @NotEmpty(message = "价格区域列表不能为空")
    private List<RoomArea> areas;
}

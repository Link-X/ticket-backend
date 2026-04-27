package com.ticket.admin.dto;

import com.ticket.core.domain.entity.SeatArea;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class SaveAreasRequest {
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;
    @NotEmpty(message = "价格区域列表不能为空")
    private List<SeatArea> areas;
}

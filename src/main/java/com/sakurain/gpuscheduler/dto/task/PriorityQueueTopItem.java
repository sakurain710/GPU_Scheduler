package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "Top queue item for task dashboard")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorityQueueTopItem {

    private Long taskId;
    private String displayName;
    private BigDecimal minMemoryGb;
    private BigDecimal agingScore;
    private Boolean isCurrentUser;
}

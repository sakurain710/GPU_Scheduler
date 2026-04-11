package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "Current user task statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserTaskStats {

    private Long runningTaskCount;
    private BigDecimal avgRunningMemoryFitScore;
    private Long queuedTaskCount;
    private BigDecimal maxQueuedAgingScore;
    private Long completedTaskCount;
    private BigDecimal weeklySuccessRate;
}

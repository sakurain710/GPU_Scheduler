package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "GPU radar snapshot for task dashboard")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuRadarSnapshot {

    private Long totalGpuCount;
    private Long availableGpuCount;
    private BigDecimal overallUtilizationRate;
    private BigDecimal systemAvgWaitSeconds;
    private BigDecimal systemFreeMemoryGb;
    private LocalDateTime nextReleaseAt;
    private Long nextReleaseInSeconds;
}

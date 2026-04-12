package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Redis health metrics")
public class RedisHealthMetrics {

    @Schema(description = "Redis status", example = "UP")
    private String status;

    @Schema(description = "Redis memory fragmentation ratio")
    private double fragmentationRatio;
}

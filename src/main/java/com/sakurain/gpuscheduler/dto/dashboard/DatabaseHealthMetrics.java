package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Database health metrics")
public class DatabaseHealthMetrics {

    @Schema(description = "Database status", example = "UP")
    private String status;

    @Schema(description = "Application-side database QPS")
    private double qps;
}

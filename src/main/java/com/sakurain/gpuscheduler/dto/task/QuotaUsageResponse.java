package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 配额使用情况
 */
@Data
@Builder
@Schema(description = "Quota usage")
public class QuotaUsageResponse {

    @Schema(description = "User id")
    private Long userId;

    @Schema(description = "Month, yyyy-MM")
    private String month;

    @Schema(description = "Used GPU seconds")
    private double usedGpuSeconds;

    @Schema(description = "GPU seconds limit")
    private double limitGpuSeconds;

    @Schema(description = "Used memory GB-seconds")
    private double usedMemoryGbSeconds;

    @Schema(description = "Memory GB-seconds limit")
    private double limitMemoryGbSeconds;

    @Schema(description = "GPU seconds usage percent")
    private double gpuUsagePercent;

    @Schema(description = "Memory usage percent")
    private double memoryUsagePercent;
}


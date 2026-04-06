package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Schema(description = "Public GPU metrics")
@Data
@Builder
public class PublicGpuMetrics {

    @Schema(description = "Total GPU count")
    private long total;

    @Schema(description = "GPU count by status")
    private Map<String, Long> countByStatus;

    @Schema(description = "Overall utilization rate")
    private String utilizationRate;

    @Schema(description = "GPU memory profile distribution")
    private Map<String, Long> memoryProfileDistribution;
}

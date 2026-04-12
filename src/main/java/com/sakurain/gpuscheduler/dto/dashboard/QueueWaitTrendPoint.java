package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Queue wait trend point")
public class QueueWaitTrendPoint {

    @Schema(description = "Bucket start time")
    private LocalDateTime bucketStart;

    @Schema(description = "Display label")
    private String label;

    @Schema(description = "Average queue wait seconds under aging")
    private double actualAgingAvgWaitSeconds;

    @Schema(description = "Average queue wait seconds under simulated FIFO")
    private double simulatedFifoAvgWaitSeconds;
}

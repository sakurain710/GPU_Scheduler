package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Realtime telemetry snapshot for dashboard")
@Data
@Builder
public class TelemetrySnapshot {

    @Schema(description = "Snapshot time")
    private LocalDateTime timestamp;

    @Schema(description = "System health")
    private SystemHealth health;

    @Schema(description = "Task metrics")
    private TaskMetrics tasks;

    @Schema(description = "GPU metrics")
    private GpuMetrics gpus;
}

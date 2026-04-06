package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Realtime public telemetry snapshot")
@Data
@Builder
public class PublicTelemetrySnapshot {

    @Schema(description = "Snapshot time")
    private LocalDateTime timestamp;

    @Schema(description = "System health")
    private SystemHealth health;

    @Schema(description = "Public task metrics")
    private PublicTaskMetrics tasks;

    @Schema(description = "Public GPU metrics")
    private PublicGpuMetrics gpus;
}

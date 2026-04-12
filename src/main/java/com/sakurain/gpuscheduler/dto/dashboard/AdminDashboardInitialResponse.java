package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Admin dashboard initial snapshot")
public class AdminDashboardInitialResponse {

    @Schema(description = "Overview metrics")
    private AdminDashboardOverviewResponse overview;

    @Schema(description = "Best-fit memory fragmentation summary")
    private MemoryFragmentationResponse memoryFragmentation;

    @Schema(description = "Dead-letter queue summary")
    private DlqSummary dlqSummary;

    @Schema(description = "Last telemetry push timestamp")
    private LocalDateTime lastTelemetryAt;
}

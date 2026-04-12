package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Scheduler thread pool metrics")
public class SchedulerThreadPoolMetrics {

    @Schema(description = "Active thread count")
    private int activeThreads;

    @Schema(description = "Core thread count")
    private int coreThreads;

    @Schema(description = "Queued task count")
    private int queuedTasks;
}

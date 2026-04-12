package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Admin dashboard overview metrics")
public class AdminDashboardOverviewResponse {

    @Schema(description = "Database health metrics")
    private DatabaseHealthMetrics mysql;

    @Schema(description = "Redis health metrics")
    private RedisHealthMetrics redis;

    @Schema(description = "Scheduler thread pool metrics")
    private SchedulerThreadPoolMetrics schedulerThreadPool;

    @Schema(description = "Circuit breaker state")
    private String circuitBreakerState;
}

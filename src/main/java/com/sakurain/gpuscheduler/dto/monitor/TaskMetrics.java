package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 任务指标 DTO
 */
@Schema(description = "Task metrics")
@Data
@Builder
public class TaskMetrics {

    @Schema(description = "Total queue length")
    private long queueLength;

    @Schema(description = "Queue length by priority bucket")
    private Map<String, Long> queueLengthByPriority;

    @Schema(description = "Task count by status")
    private Map<String, Long> taskCountByStatus;

    @Schema(description = "Average wait seconds by priority bucket")
    private Map<String, Double> avgWaitSecondsByPriority;

    @Schema(description = "Average dispatch latency in seconds (enqueue -> running)")
    private Double avgDispatchLatencySeconds;

    @Schema(description = "Average turnaround in seconds (enqueue -> finished)")
    private Double avgTurnaroundSeconds;

    @Schema(description = "Task completion rate")
    private String completionRate;

    @Schema(description = "Task failure rate")
    private String failureRate;

    @Schema(description = "Failure reason distribution")
    private Map<String, Long> failureReasons;

    @Schema(description = "Retry queue size")
    private Long retryQueueSize;

    @Schema(description = "Dead-letter queue size")
    private Long dlqSize;

    @Schema(description = "Pending approval task count")
    private Long pendingApprovalCount;
}

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

    /** 队列当前长度 */
    @Schema(description = "Total queue length")
    private long queueLength;

    @Schema(description = "Queue length by priority bucket")
    private Map<String, Long> queueLengthByPriority;

    /** 各状态任务数量 */
    @Schema(description = "Task count by status")
    private Map<String, Long> taskCountByStatus;

    /** 各优先级平均等待时间（秒） */
    @Schema(description = "Average wait seconds by priority bucket")
    private Map<String, Double> avgWaitSecondsByPriority;

    @Schema(description = "Average dispatch latency in seconds (enqueue -> running)")
    private Double avgDispatchLatencySeconds;

    @Schema(description = "Average turnaround in seconds (enqueue -> finished)")
    private Double avgTurnaroundSeconds;

    /** 任务完成率（COMPLETED / 终态总数） */
    @Schema(description = "Task completion rate")
    private String completionRate;

    /** 任务失败率（FAILED / 终态总数） */
    @Schema(description = "Task failure rate")
    private String failureRate;

    /** 失败原因分布（errorMessage 前缀 → 次数） */
    @Schema(description = "Failure reason distribution")
    private Map<String, Long> failureReasons;
}

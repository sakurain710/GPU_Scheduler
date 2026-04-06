package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Schema(description = "Public task metrics")
@Data
@Builder
public class PublicTaskMetrics {

    @Schema(description = "Total queue length")
    private long queueLength;

    @Schema(description = "Queue length by priority bucket")
    private Map<String, Long> queueLengthByPriority;

    @Schema(description = "Task count by status")
    private Map<String, Long> taskCountByStatus;

    @Schema(description = "Average wait seconds by priority bucket")
    private Map<String, Double> avgWaitSecondsByPriority;

    @Schema(description = "Average dispatch latency in seconds")
    private Double avgDispatchLatencySeconds;

    @Schema(description = "Average turnaround in seconds")
    private Double avgTurnaroundSeconds;

    @Schema(description = "Dispatch latency percentiles in seconds")
    private Map<String, Double> dispatchLatencyPercentilesSeconds;

    @Schema(description = "Queue age histogram")
    private Map<String, Long> queueAgeHistogram;

    @Schema(description = "Task completion rate")
    private String completionRate;

    @Schema(description = "Task failure rate")
    private String failureRate;
}

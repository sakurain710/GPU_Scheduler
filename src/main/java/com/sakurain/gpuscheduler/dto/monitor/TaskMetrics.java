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
    private long queueLength;

    /** 各状态任务数量 */
    private Map<String, Long> taskCountByStatus;

    /** 各优先级平均等待时间（秒） */
    private Map<String, Double> avgWaitSecondsByPriority;

    /** 任务完成率（COMPLETED / 终态总数） */
    private String completionRate;

    /** 任务失败率（FAILED / 终态总数） */
    private String failureRate;

    /** 失败原因分布（errorMessage 前缀 → 次数） */
    private Map<String, Long> failureReasons;
}

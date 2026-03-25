package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统整体健康状态 DTO
 */
@Schema(description = "System health")
@Data
@Builder
public class SystemHealth {

    /** 整体状态：UP / DEGRADED / DOWN */
    private String status;

    /** MySQL 连通性 */
    private String dbStatus;

    /** Redis 连通性 */
    private String redisStatus;

    /** 调度器线程池状态 */
    private String schedulerStatus;

    /** 熔断器状态（CLOSED / OPEN / HALF_OPEN） */
    private String circuitBreakerState;

    /** 队列中最老任务的等待秒数（-1 表示队列为空） */
    private long oldestQueuedTaskSeconds;
}

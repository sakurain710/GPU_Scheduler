package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.monitor.GpuMetrics;
import com.sakurain.gpuscheduler.dto.monitor.SystemHealth;
import com.sakurain.gpuscheduler.dto.monitor.TaskMetrics;
import com.sakurain.gpuscheduler.service.MonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控与可观测性控制器
 * <p>
 * 路由：
 *   GET /api/health   — 系统整体健康状态（仅 ADMIN）
 *   GET /api/metrics  — 任务 + GPU 聚合指标（仅 ADMIN）
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /**
     * 系统整体健康状态
     * <p>
     * 返回 DB / Redis 连通性、熔断器状态、队列最老任务等待时间
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<SystemHealth> health() {
        return Result.success(monitoringService.getSystemHealth());
    }

    /**
     * 聚合指标（任务 + GPU）
     * <p>
     * 包含：队列长度、状态分布、等待时间、完成率、失败率、GPU 利用率、VRAM 碎片化
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<MetricsResponse> metrics() {
        TaskMetrics taskMetrics = monitoringService.getTaskMetrics();
        GpuMetrics gpuMetrics = monitoringService.getGpuMetrics();
        return Result.success(new MetricsResponse(taskMetrics, gpuMetrics));
    }

    /** 内联响应包装，避免额外 DTO 文件 */
    public record MetricsResponse(TaskMetrics tasks, GpuMetrics gpus) {}
}

package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.monitor.PublicGpuMetrics;
import com.sakurain.gpuscheduler.dto.monitor.PublicTaskMetrics;
import com.sakurain.gpuscheduler.dto.monitor.SystemHealth;
import com.sakurain.gpuscheduler.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "公开首页监控", description = "面向未登录访客的聚合监控概览接口")
@RestController
@RequestMapping("/api/public/overview")
public class PublicOverviewController {

    private final MonitoringService monitoringService;

    public PublicOverviewController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Operation(summary = "获取公开健康状态")
    @GetMapping("/health")
    public Result<SystemHealth> health() {
        return Result.success(monitoringService.getSystemHealth());
    }

    @Operation(summary = "获取公开概览指标")
    @GetMapping("/metrics")
    public Result<PublicMetricsResponse> metrics() {
        return Result.success(new PublicMetricsResponse(
                monitoringService.getPublicTaskMetrics(),
                monitoringService.getPublicGpuMetrics()
        ));
    }

    public record PublicMetricsResponse(
            @Schema(description = "公开任务指标") PublicTaskMetrics tasks,
            @Schema(description = "公开 GPU 指标") PublicGpuMetrics gpus) {
    }
}

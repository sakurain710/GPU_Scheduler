package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.monitor.GpuMetrics;
import com.sakurain.gpuscheduler.dto.monitor.SystemHealth;
import com.sakurain.gpuscheduler.dto.monitor.TaskMetrics;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控与可观测接口。
 */
@Slf4j
@Tag(name = "监控与可观测", description = "系统与业务监控接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@Validated
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final GpuTaskService gpuTaskService;

    public MonitoringController(MonitoringService monitoringService,
                                GpuTaskService gpuTaskService) {
        this.monitoringService = monitoringService;
        this.gpuTaskService = gpuTaskService;
    }

    @Operation(summary = "获取系统健康状态", description = "仅管理员或监控读权限")
    @GetMapping("/health")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','monitoring:read')")
    public Result<SystemHealth> health() {
        return Result.success(monitoringService.getSystemHealth());
    }

    @Operation(summary = "获取合并指标", description = "任务 + GPU 监控指标")
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','monitoring:read')")
    public Result<MetricsResponse> metrics() {
        TaskMetrics taskMetrics = monitoringService.getTaskMetrics();
        GpuMetrics gpuMetrics = monitoringService.getGpuMetrics();
        return Result.success(new MetricsResponse(taskMetrics, gpuMetrics));
    }

    @Operation(summary = "获取全局任务流", description = "用于监控大屏实时任务表格")
    @GetMapping("/metrics/tasks/stream")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','monitoring:read')")
    public Result<IPage<TaskResponse>> globalTaskStream(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(200) Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean activeOnly,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return Result.success(gpuTaskService.listGlobalTasks(page, size, status, activeOnly, sortBy, sortDir));
    }

    public record MetricsResponse(
            @Schema(description = "任务指标") TaskMetrics tasks,
            @Schema(description = "GPU 指标") GpuMetrics gpus) {
    }
}

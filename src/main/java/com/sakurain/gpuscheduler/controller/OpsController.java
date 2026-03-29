package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.task.RejectTaskRequest;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.scheduler.CircuitBreakerService;
import com.sakurain.gpuscheduler.scheduler.TaskDispatcher;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.TaskNotificationService;
import com.sakurain.gpuscheduler.service.TaskRetryDlqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运维控制接口
 */
@Tag(name = "运维控制", description = "调度器、熔断器、死信队列")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
public class OpsController {

    private final TaskDispatcher taskDispatcher;
    private final CircuitBreakerService circuitBreakerService;
    private final TaskRetryDlqService retryDlqService;
    private final GpuTaskService gpuTaskService;
    private final TaskNotificationService taskNotificationService;

    public OpsController(TaskDispatcher taskDispatcher,
                         CircuitBreakerService circuitBreakerService,
                         TaskRetryDlqService retryDlqService,
                         GpuTaskService gpuTaskService,
                         TaskNotificationService taskNotificationService) {
        this.taskDispatcher = taskDispatcher;
        this.circuitBreakerService = circuitBreakerService;
        this.retryDlqService = retryDlqService;
        this.gpuTaskService = gpuTaskService;
        this.taskNotificationService = taskNotificationService;
    }

    @Operation(summary = "暂停调度器")
    @PostMapping("/dispatcher/pause")
    public Result<Map<String, Object>> pauseDispatcher() {
        taskDispatcher.pauseDispatch();
        return Result.success(Map.of("paused", true));
    }

    @Operation(summary = "恢复调度器")
    @PostMapping("/dispatcher/resume")
    public Result<Map<String, Object>> resumeDispatcher() {
        taskDispatcher.resumeDispatch();
        return Result.success(Map.of("paused", false));
    }

    @Operation(summary = "查询调度器状态")
    @GetMapping("/dispatcher/status")
    public Result<Map<String, Object>> dispatcherStatus() {
        return Result.success(Map.of("paused", taskDispatcher.isPaused()));
    }

    @Operation(summary = "重置熔断器")
    @PostMapping("/circuit-breaker/reset")
    public Result<Map<String, Object>> resetCircuitBreaker() {
        circuitBreakerService.reset();
        return Result.success(Map.of(
                "state", circuitBreakerService.getState().name(),
                "failureCount", circuitBreakerService.getFailureCount()
        ));
    }

    @Operation(summary = "查看死信队列")
    @GetMapping("/dlq")
    public Result<Map<String, Object>> listDlq() {
        return Result.success(Map.of(
                "size", retryDlqService.dlqSize(),
                "items", retryDlqService.listDlq(100)
        ));
    }

    @Operation(summary = "清空死信队列")
    @PostMapping("/dlq/clear")
    public Result<Map<String, Object>> clearDlq() {
        long removed = retryDlqService.clearDlq();
        return Result.success(Map.of("removed", removed));
    }

    @Operation(summary = "重处理死信任务")
    @PostMapping("/dlq/{taskId}/reprocess")
    public Result<Map<String, Object>> reprocessDlqTask(@PathVariable Long taskId) {
        boolean ok = retryDlqService.reprocessDlqTask(taskId);
        return Result.success(Map.of("taskId", taskId, "reprocessed", ok));
    }

    @Operation(summary = "强制任务重入队")
    @PostMapping("/tasks/{taskId}/force-requeue")
    public Result<Void> forceRequeue(@PathVariable Long taskId) {
        gpuTaskService.transition(taskId, TaskStatus.QUEUED, null, null);
        return Result.success();
    }

    @Operation(summary = "强制任务失败")
    @PostMapping("/tasks/{taskId}/force-fail")
    public Result<Void> forceFailTask(@PathVariable Long taskId,
                                      @RequestBody(required = false) RejectTaskRequest request) {
        String reason = request != null ? request.getReason() : "Forced failed by operator";
        gpuTaskService.forceFailTask(taskId, getCurrentUserId(), reason);
        return Result.success();
    }

    @Operation(summary = "抢占运行任务并重入队")
    @PostMapping("/tasks/{taskId}/preempt")
    public Result<Map<String, Object>> preemptTask(@PathVariable Long taskId,
                                                   @RequestBody(required = false) RejectTaskRequest request) {
        String reason = request != null ? request.getReason() : "Preempted by operator";
        gpuTaskService.preemptTask(taskId, getCurrentUserId(), reason);
        return Result.success(Map.of("taskId", taskId, "preempted", true));
    }

    @Operation(summary = "查询通知重试队列状态")
    @GetMapping("/notification/webhook/retry/status")
    public Result<Map<String, Object>> webhookRetryStatus() {
        return Result.success(Map.of(
                "queueSize", taskNotificationService.webhookRetryQueueSize()
        ));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }
}

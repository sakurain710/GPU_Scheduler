package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.task.BatchApproveRequest;
import com.sakurain.gpuscheduler.dto.task.RejectTaskRequest;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskAdminListItem;
import com.sakurain.gpuscheduler.dto.task.TaskDashboardResponse;
import com.sakurain.gpuscheduler.dto.task.TaskExecutionLogResponse;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.TaskDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "GPU任务管理", description = "Submit, query and approve GPU tasks")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tasks")
@Validated
public class GpuTaskController {

    private final GpuTaskService gpuTaskService;
    private final TaskDashboardService taskDashboardService;

    @Autowired
    public GpuTaskController(GpuTaskService gpuTaskService,
                             TaskDashboardService taskDashboardService) {
        this.gpuTaskService = gpuTaskService;
        this.taskDashboardService = taskDashboardService;
    }

    @Operation(summary = "提交任务")
    @PostMapping("/submit")
    public Result<TaskResponse> submitTask(@Valid @RequestBody SubmitTaskRequest request) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        TaskResponse response = gpuTaskService.submitTask(
                request,
                currentUser.getUserId(),
                currentUser.getRoleCodes()
        );
        return Result.success("任务提交成功", response);
    }

    @Operation(summary = "根据任务ID查询")
    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTask(@PathVariable Long taskId) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        TaskResponse response = gpuTaskService.getTask(taskId, currentUser.getUserId(), currentUser.getRoleCodes());
        return Result.success(response);
    }

    @Operation(summary = "获取任务日志")
    @GetMapping("/{taskId}/logs")
    public Result<TaskExecutionLogResponse> getTaskLogs(@PathVariable Long taskId) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        TaskExecutionLogResponse response = gpuTaskService.getTaskExecutionLogs(
                taskId,
                currentUser.getUserId(),
                currentUser.getRoleCodes()
        );
        return Result.success(response);
    }

    @Operation(summary = "获取当前用户任务列表", description = "Support paging, status filter and sorting")
    @GetMapping("/my")
    public Result<IPage<TaskResponse>> listMyTasks(
            @Parameter(description = "Page number, starts from 1") @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer size,
            @Parameter(description = "Task status filter") @RequestParam(required = false) Integer status,
            @Parameter(description = "Sort field: createdAt/basePriority/enqueueAt/status/id")
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc/desc")
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        Long userId = getCurrentUserId();
        return Result.success(gpuTaskService.listUserTasks(userId, page, size, status, sortBy, sortDir));
    }

    @Operation(summary = "管理员获取任务列表", description = "Filter by taskType/status and sort by task id")
    @GetMapping("/admin/list")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<IPage<TaskAdminListItem>> listAdminTasks(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return Result.success(gpuTaskService.listAdminTasks(page, size, taskType, status, sortDir));
    }

    @Operation(summary = "Get current user task dashboard")
    @GetMapping("/dashboard")
    public Result<TaskDashboardResponse> getDashboard(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        Long userId = getCurrentUserId();
        return Result.success(taskDashboardService.getDashboard(userId, page, size, status, sortBy, sortDir));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(@PathVariable Long taskId,
                                   @RequestBody(required = false) RejectTaskRequest request) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        String reason = request != null ? request.getReason() : null;
        gpuTaskService.cancelTask(taskId, currentUser.getUserId(), currentUser.getRoleCodes(), reason);
        return Result.success();
    }

    @Operation(summary = "待审核任务列表")
    @GetMapping("/approval/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:read')")
    public Result<IPage<TaskResponse>> listPendingApprovals(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir) {
        return Result.success(gpuTaskService.listPendingApprovals(page, size, sortBy, sortDir));
    }

    @Operation(summary = "审核通过任务")
    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:review')")
    public Result<TaskResponse> approveTask(@PathVariable Long taskId) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        TaskResponse response = gpuTaskService.approveTask(taskId, currentUser.getUserId());
        return Result.success(response);
    }

    @Operation(summary = "审核不通过任务")
    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:review')")
    public Result<TaskResponse> rejectTask(@PathVariable Long taskId,
                                           @RequestBody(required = false) RejectTaskRequest request) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        String reason = request != null ? request.getReason() : null;
        TaskResponse response = gpuTaskService.rejectTask(taskId, currentUser.getUserId(), reason);
        return Result.success(response);
    }

    @Operation(summary = "Batch approve tasks")
    @PostMapping("/approval/batch/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:review')")
    public Result<List<TaskResponse>> batchApproveTasks(@Valid @RequestBody BatchApproveRequest request) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        return Result.success(gpuTaskService.batchApproveTasks(request.getTaskIds(), currentUser.getUserId()));
    }

    @Operation(summary = "Batch reject tasks")
    @PostMapping("/approval/batch/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:review')")
    public Result<List<TaskResponse>> batchRejectTasks(@Valid @RequestBody BatchApproveRequest request) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        return Result.success(gpuTaskService.batchRejectTasks(request.getTaskIds(), currentUser.getUserId(), request.getReason()));
    }

    private Long getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }

    private CustomUserDetails getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (CustomUserDetails) auth.getPrincipal();
    }
}

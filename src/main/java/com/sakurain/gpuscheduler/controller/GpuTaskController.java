package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.task.RejectTaskRequest;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GPU任务管理
 */
@Slf4j
@Tag(name = "GPU任务管理", description = "提交、查询和取消GPU任务")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/task")
public class GpuTaskController {

    private final GpuTaskService gpuTaskService;

    @Autowired
    public GpuTaskController(GpuTaskService gpuTaskService) {
        this.gpuTaskService = gpuTaskService;
    }

    /**
     * 提交GPU计算任务
     */
    @Operation(summary = "提交GPU任务")
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

    /**
     * 查询任务详情
     */
    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTask(@PathVariable Long taskId) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        TaskResponse response = gpuTaskService.getTask(taskId, currentUser.getUserId(), currentUser.getRoleCodes());
        return Result.success(response);
    }

    /**
     * 用户任务列表
     */
    @Operation(summary = "列出当前用户的任务", description = "支持分页和可选状态过滤")
    @GetMapping("/my")
    public Result<IPage<TaskResponse>> listMyTasks(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "任务状态码过滤") @RequestParam(required = false) Integer status) {
        Long userId = getCurrentUserId();
        return Result.success(gpuTaskService.listUserTasks(userId, page, size, status));
    }

    /**
     * 取消任务
     */
    @Operation(summary = "取消任务")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(@PathVariable Long taskId) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        gpuTaskService.cancelTask(taskId, currentUser.getUserId(), currentUser.getRoleCodes());
        return Result.success();
    }

    @Operation(summary = "待审批任务列表")
    @GetMapping("/approval/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public Result<IPage<TaskResponse>> listPendingApprovals(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(gpuTaskService.listPendingApprovals(page, size));
    }

    @Operation(summary = "审批通过任务")
    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public Result<TaskResponse> approveTask(@PathVariable Long taskId) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        TaskResponse response = gpuTaskService.approveTask(taskId, currentUser.getUserId());
        return Result.success(response);
    }

    @Operation(summary = "审批拒绝任务")
    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public Result<TaskResponse> rejectTask(@PathVariable Long taskId,
                                           @RequestBody(required = false) RejectTaskRequest request) {
        CustomUserDetails currentUser = getCurrentUserDetails();
        String reason = request != null ? request.getReason() : null;
        TaskResponse response = gpuTaskService.rejectTask(taskId, currentUser.getUserId(), reason);
        return Result.success(response);
    }

    private Long getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }

    private CustomUserDetails getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (CustomUserDetails) auth.getPrincipal();
    }
}

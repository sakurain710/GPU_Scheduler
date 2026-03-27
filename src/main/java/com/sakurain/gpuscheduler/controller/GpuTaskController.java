package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * GPU任务控制器
 */
@Slf4j
@Tag(name = "Task Management", description = "Submit, query and cancel GPU tasks")
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
    @Operation(summary = "Submit GPU task")
    @PostMapping("/submit")
    public Result<TaskResponse> submitTask(@Valid @RequestBody SubmitTaskRequest request) {
        Long userId = getCurrentUserId();
        TaskResponse response = gpuTaskService.submitTask(request, userId);
        return Result.success("任务提交成功", response);
    }

    /**
     * 查询任务详情
     */
    @Operation(summary = "Get task detail")
    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTask(@PathVariable Long taskId) {
        TaskResponse response = gpuTaskService.getTask(taskId);
        return Result.success(response);
    }

    /**
     * 用户任务列表
     */
    @Operation(summary = "List current user's tasks", description = "Supports pagination and optional status filter")
    @GetMapping("/my")
    public Result<IPage<TaskResponse>> listMyTasks(
            @Parameter(description = "Page number, starts from 1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "Task status code filter") @RequestParam(required = false) Integer status) {
        Long userId = getCurrentUserId();
        return Result.success(gpuTaskService.listUserTasks(userId, page, size, status));
    }

    /**
     * 取消任务
     */
    @Operation(summary = "Cancel task")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(@PathVariable Long taskId) {
        Long userId = getCurrentUserId();
        gpuTaskService.transition(taskId, TaskStatus.CANCELLED, null, userId);
        return Result.success();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }
}

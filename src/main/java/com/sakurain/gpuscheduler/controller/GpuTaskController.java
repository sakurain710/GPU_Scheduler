package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * GPU任务控制器
 */
@Slf4j
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
    @PostMapping("/submit")
    public Result<TaskResponse> submitTask(@Valid @RequestBody SubmitTaskRequest request) {
        Long userId = getCurrentUserId();
        TaskResponse response = gpuTaskService.submitTask(request, userId);
        return Result.success("任务提交成功", response);
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTask(@PathVariable Long taskId) {
        TaskResponse response = gpuTaskService.getTask(taskId);
        return Result.success(response);
    }

    /**
     * 取消任务
     */
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

package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.config.TaskSubmissionPolicyConfig;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * GPU任务服务 — 提交、入队、状态转换
 */
@Slf4j
@Service
public class GpuTaskService {

    private final GpuTaskMapper taskMapper;
    private final GpuTaskLogMapper taskLogMapper;
    private final TaskStateMachine stateMachine;
    private final TaskPriorityQueue priorityQueue;
    private final TaskAgingScheduler agingScheduler;
    private final TaskSubmissionPolicyConfig submissionPolicy;

    public GpuTaskService(GpuTaskMapper taskMapper,
                          GpuTaskLogMapper taskLogMapper,
                          TaskStateMachine stateMachine,
                          TaskPriorityQueue priorityQueue,
                          TaskAgingScheduler agingScheduler,
                          TaskSubmissionPolicyConfig submissionPolicy) {
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.stateMachine = stateMachine;
        this.priorityQueue = priorityQueue;
        this.agingScheduler = agingScheduler;
        this.submissionPolicy = submissionPolicy;
    }

    /**
     * 提交任务 — 持久化为PENDING，然后立即转换为QUEUED并入队Redis
     */
    @Transactional
    public TaskResponse submitTask(SubmitTaskRequest request, Long userId) {
        return submitTask(request, userId, List.of());
    }

    @Transactional
    public TaskResponse submitTask(SubmitTaskRequest request, Long userId, List<String> roleCodes) {
        validateSubmissionPolicy(request, userId, roleCodes);

        // 1. 构建实体，初始状态 PENDING
        GpuTask task = GpuTask.builder()
                .userId(userId)
                .title(request.getTitle())
                .description(request.getDescription())
                .taskType(request.getTaskType())
                .minMemoryGb(request.getMinMemoryGb())
                .computeUnitsGflop(request.getComputeUnitsGflop())
                .basePriority(request.getBasePriority() != null ? request.getBasePriority() : 5)
                .status(TaskStatus.PENDING.getCode())
                .build();
        taskMapper.insert(task);
        log.info("任务已创建: taskId={}, userId={}", task.getId(), userId);

        // 2. PENDING → QUEUED，入队Redis
        transition(task.getId(), TaskStatus.QUEUED, null, userId);

        // 3. 重新查询完整数据返回
        GpuTask saved = taskMapper.selectById(task.getId());
        return toResponse(saved);
    }

    /**
     * 通用状态转换 — 校验合法性、更新DB、写审计日志、联动Redis队列
     *
     * @param taskId     任务ID
     * @param target     目标状态
     * @param gpuId      关联GPU（仅RUNNING时需要）
     * @param operatorId 操作者用户ID
     */
    @Transactional
    public void transition(Long taskId, TaskStatus target, Long gpuId, Long operatorId) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("任务不存在: " + taskId);
        }

        TaskStatus from = TaskStatus.fromCode(task.getStatus());
        stateMachine.validateTransition(from, target);

        // 更新DB状态
        task.setStatus(target.getCode());
        if (target == TaskStatus.QUEUED) {
            task.setEnqueueAt(java.time.LocalDateTime.now());
        }
        if (target == TaskStatus.RUNNING && gpuId != null) {
            task.setGpuId(gpuId);
            task.setDispatchedAt(java.time.LocalDateTime.now());
        }
        if (target.isTerminal()) {
            task.setFinishedAt(java.time.LocalDateTime.now());
        }
        taskMapper.updateById(task);

        // 联动Redis队列
        if (target == TaskStatus.QUEUED) {
            // 使用有效优先级（包含老化加成）入队
            double effectivePriority = agingScheduler.calculateEffectivePriority(task);
            priorityQueue.enqueue(taskId, effectivePriority);
        } else if (from == TaskStatus.QUEUED) {
            // 从QUEUED转出时，移除队列
            priorityQueue.remove(taskId);
        }

        // 写审计日志
        GpuTaskLog logEntry = GpuTaskLog.builder()
                .taskId(taskId)
                .gpuId(gpuId)
                .event(stateMachine.resolveEvent(target))
                .oldStatus(from.getCode())
                .newStatus(target.getCode())
                .operatorId(operatorId)
                .build();
        taskLogMapper.insert(logEntry);

        log.info("任务状态转换: taskId={}, {} -> {}", taskId, from.getLabel(), target.getLabel());
    }

    /**
     * 根据ID查询任务
     */
    public TaskResponse getTask(Long taskId) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("任务不存在: " + taskId);
        }
        return toResponse(task);
    }

    public TaskResponse getTask(Long taskId, Long requesterId, List<String> roleCodes) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        validateTaskOwnerOrApprover(task, requesterId, roleCodes);
        return toResponse(task);
    }

    @Transactional
    public void cancelTask(Long taskId, Long requesterId, List<String> roleCodes) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        validateTaskOwnerOrApprover(task, requesterId, roleCodes);
        transition(taskId, TaskStatus.CANCELLED, null, requesterId);
    }

    public IPage<TaskResponse> listUserTasks(Long userId, Integer page, Integer size, Integer status) {
        Page<GpuTask> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getUserId, userId)
                .orderByDesc(GpuTask::getCreatedAt);

        if (status != null) {
            wrapper.eq(GpuTask::getStatus, status);
        }

        IPage<GpuTask> taskPage = taskMapper.selectPage(pageParam, wrapper);
        return taskPage.convert(this::toResponse);
    }

    private void validateSubmissionPolicy(SubmitTaskRequest request, Long userId, List<String> roleCodes) {
        int priority = request.getBasePriority() != null ? request.getBasePriority() : 5;
        boolean hasApprovalRole = roleCodes != null
                && roleCodes.stream().anyMatch(submissionPolicy.getApproverRoles()::contains);

        if (priority >= submissionPolicy.getHighPriorityThreshold() && !hasApprovalRole) {
            throw new BusinessException(
                    "TASK_APPROVAL_REQUIRED",
                    "High-priority tasks require ADMIN approval role",
                    403
            );
        }

        if (!hasApprovalRole) {
            Long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<GpuTask>()
                    .eq(GpuTask::getUserId, userId)
                    .in(GpuTask::getStatus,
                            TaskStatus.PENDING.getCode(),
                            TaskStatus.QUEUED.getCode(),
                            TaskStatus.RUNNING.getCode()));

            if (activeTaskCount != null && activeTaskCount >= submissionPolicy.getMaxActiveTasksPerUser()) {
                throw new BusinessException(
                        "TASK_QUOTA_EXCEEDED",
                        "Active task quota exceeded, please wait for running tasks to finish",
                        429
                );
            }
        }
    }

    private void validateTaskOwnerOrApprover(GpuTask task, Long requesterId, List<String> roleCodes) {
        boolean hasApprovalRole = roleCodes != null
                && roleCodes.stream().anyMatch(submissionPolicy.getApproverRoles()::contains);
        if (!hasApprovalRole && !task.getUserId().equals(requesterId)) {
            throw new BusinessException("TASK_FORBIDDEN", "No permission to access this task", 403);
        }
    }

    private TaskResponse toResponse(GpuTask task) {
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        return TaskResponse.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .gpuId(task.getGpuId())
                .title(task.getTitle())
                .description(task.getDescription())
                .taskType(task.getTaskType())
                .minMemoryGb(task.getMinMemoryGb())
                .computeUnitsGflop(task.getComputeUnitsGflop())
                .basePriority(task.getBasePriority())
                .status(status.getCode())
                .statusLabel(status.getLabel())
                .estimatedSeconds(task.getEstimatedSeconds())
                .actualSeconds(task.getActualSeconds())
                .errorMessage(task.getErrorMessage())
                .enqueueAt(task.getEnqueueAt())
                .dispatchedAt(task.getDispatchedAt())
                .estimatedFinishAt(task.getEstimatedFinishAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }
}

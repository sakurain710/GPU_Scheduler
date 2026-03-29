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
 * GPU任务服务
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

    @Transactional
    public TaskResponse submitTask(SubmitTaskRequest request, Long userId) {
        return submitTask(request, userId, List.of());
    }

    /**
     * 提交任务：
     * 1) 普通任务直接入队
     * 2) 高优先级且无审批权限的任务进入待审批状态
     */
    @Transactional
    public TaskResponse submitTask(SubmitTaskRequest request, Long userId, List<String> roleCodes) {
        validateSubmissionPolicy(request, userId, roleCodes);
        boolean requiresApproval = isApprovalRequired(request, roleCodes);

        GpuTask task = GpuTask.builder()
                .userId(userId)
                .title(request.getTitle())
                .description(request.getDescription())
                .taskType(request.getTaskType())
                .minMemoryGb(request.getMinMemoryGb())
                .computeUnitsGflop(request.getComputeUnitsGflop())
                .basePriority(request.getBasePriority() != null ? request.getBasePriority() : 5)
                .status(requiresApproval ? TaskStatus.PENDING_APPROVAL.getCode() : TaskStatus.PENDING.getCode())
                .build();
        taskMapper.insert(task);

        if (requiresApproval) {
            writeAudit(task.getId(), null, TaskStatus.PENDING_APPROVAL, TaskStatus.PENDING_APPROVAL, userId);
        } else {
            transition(task.getId(), TaskStatus.QUEUED, null, userId);
        }

        return toResponse(taskMapper.selectById(task.getId()));
    }

    @Transactional
    public void transition(Long taskId, TaskStatus target, Long gpuId, Long operatorId) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }

        TaskStatus from = TaskStatus.fromCode(task.getStatus());
        stateMachine.validateTransition(from, target);

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

        if (target == TaskStatus.QUEUED) {
            double effectivePriority = agingScheduler.calculateEffectivePriority(task);
            priorityQueue.enqueue(taskId, effectivePriority);
        } else if (from == TaskStatus.QUEUED) {
            priorityQueue.remove(taskId);
        }

        writeAudit(taskId, gpuId, from, target, operatorId);
    }

    public TaskResponse getTask(Long taskId) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
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

        return taskMapper.selectPage(pageParam, wrapper).convert(this::toResponse);
    }

    /**
     * 审批人查看待审批任务
     */
    public IPage<TaskResponse> listPendingApprovals(Integer page, Integer size) {
        Page<GpuTask> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getStatus, TaskStatus.PENDING_APPROVAL.getCode())
                .orderByAsc(GpuTask::getCreatedAt);
        return taskMapper.selectPage(pageParam, wrapper).convert(this::toResponse);
    }

    @Transactional
    public TaskResponse approveTask(Long taskId, Long approverId) {
        transition(taskId, TaskStatus.QUEUED, null, approverId);
        return getTask(taskId);
    }

    @Transactional
    public TaskResponse rejectTask(Long taskId, Long approverId, String reason) {
        if (reason != null && !reason.isBlank()) {
            GpuTask update = new GpuTask();
            update.setId(taskId);
            update.setErrorMessage(reason);
            taskMapper.updateById(update);
        }
        transition(taskId, TaskStatus.REJECTED, null, approverId);
        return getTask(taskId);
    }

    private void validateSubmissionPolicy(SubmitTaskRequest request, Long userId, List<String> roleCodes) {
        if (!hasApprovalRole(roleCodes)) {
            Long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<GpuTask>()
                    .eq(GpuTask::getUserId, userId)
                    .in(GpuTask::getStatus,
                            TaskStatus.PENDING.getCode(),
                            TaskStatus.PENDING_APPROVAL.getCode(),
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

    private boolean isApprovalRequired(SubmitTaskRequest request, List<String> roleCodes) {
        int priority = request.getBasePriority() != null ? request.getBasePriority() : 5;
        return priority >= submissionPolicy.getHighPriorityThreshold() && !hasApprovalRole(roleCodes);
    }

    private boolean hasApprovalRole(List<String> roleCodes) {
        return roleCodes != null && roleCodes.stream().anyMatch(submissionPolicy.getApproverRoles()::contains);
    }

    private void validateTaskOwnerOrApprover(GpuTask task, Long requesterId, List<String> roleCodes) {
        boolean hasApprovalRole = hasApprovalRole(roleCodes);
        if (!hasApprovalRole && !task.getUserId().equals(requesterId)) {
            throw new BusinessException("TASK_FORBIDDEN", "No permission to access this task", 403);
        }
    }

    private void writeAudit(Long taskId, Long gpuId, TaskStatus oldStatus, TaskStatus newStatus, Long operatorId) {
        GpuTaskLog logEntry = GpuTaskLog.builder()
                .taskId(taskId)
                .gpuId(gpuId)
                .event(stateMachine.resolveEvent(newStatus))
                .oldStatus(oldStatus.getCode())
                .newStatus(newStatus.getCode())
                .operatorId(operatorId)
                .build();
        taskLogMapper.insert(logEntry);
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

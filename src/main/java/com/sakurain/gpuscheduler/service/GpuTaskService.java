package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.config.TaskSubmissionPolicyConfig;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import com.sakurain.gpuscheduler.util.PaginationUtils;
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
    private final GpuMapper gpuMapper;
    private final GpuTaskLogMapper taskLogMapper;
    private final TaskStateMachine stateMachine;
    private final TaskPriorityQueue priorityQueue;
    private final TaskAgingScheduler agingScheduler;
    private final TaskSubmissionPolicyConfig submissionPolicy;
    private final TaskNotificationService taskNotificationService;

    public GpuTaskService(GpuTaskMapper taskMapper,
                          GpuMapper gpuMapper,
                          GpuTaskLogMapper taskLogMapper,
                          TaskStateMachine stateMachine,
                          TaskPriorityQueue priorityQueue,
                          TaskAgingScheduler agingScheduler,
                          TaskSubmissionPolicyConfig submissionPolicy,
                          TaskNotificationService taskNotificationService) {
        this.taskMapper = taskMapper;
        this.gpuMapper = gpuMapper;
        this.taskLogMapper = taskLogMapper;
        this.stateMachine = stateMachine;
        this.priorityQueue = priorityQueue;
        this.agingScheduler = agingScheduler;
        this.submissionPolicy = submissionPolicy;
        this.taskNotificationService = taskNotificationService;
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
            // 支持抢占后重入队：清理运行态字段
            task.setGpuId(null);
            task.setDispatchedAt(null);
            task.setEstimatedFinishAt(null);
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
        taskNotificationService.notifyTaskStatus(
                taskId,
                task.getUserId(),
                from,
                target,
                task.getErrorMessage()
        );
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

    public IPage<TaskResponse> listUserTasks(Long userId,
                                             Integer page,
                                             Integer size,
                                             Integer status,
                                             String sortBy,
                                             String sortDir) {
        Page<GpuTask> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 10, 200)
        );
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getUserId, userId);

        if (status != null) {
            wrapper.eq(GpuTask::getStatus, status);
        }
        applyTaskSort(wrapper, sortBy, sortDir, true);

        return taskMapper.selectPage(pageParam, wrapper).convert(this::toResponse);
    }

    /**
     * 审批人查看待审批任务
     */
    public IPage<TaskResponse> listPendingApprovals(Integer page,
                                                    Integer size,
                                                    String sortBy,
                                                    String sortDir) {
        Page<GpuTask> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 10, 200)
        );
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getStatus, TaskStatus.PENDING_APPROVAL.getCode());
        applyTaskSort(wrapper, sortBy, sortDir, false);
        return taskMapper.selectPage(pageParam, wrapper).convert(this::toResponse);
    }

    private void applyTaskSort(LambdaQueryWrapper<GpuTask> wrapper,
                               String sortBy,
                               String sortDir,
                               boolean defaultDesc) {
        boolean asc = sortDir == null ? !defaultDesc : !"desc".equalsIgnoreCase(sortDir);
        String key = sortBy == null ? "createdAt" : sortBy;
        switch (key) {
            case "basePriority" -> wrapper.orderBy(true, asc, GpuTask::getBasePriority);
            case "enqueueAt" -> wrapper.orderBy(true, asc, GpuTask::getEnqueueAt);
            case "status" -> wrapper.orderBy(true, asc, GpuTask::getStatus);
            case "id" -> wrapper.orderBy(true, asc, GpuTask::getId);
            default -> wrapper.orderBy(true, asc, GpuTask::getCreatedAt);
        }
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

    /**
     * 抢占运行任务并重入队
     */
    @Transactional
    public TaskResponse preemptTask(Long taskId, Long operatorId, String reason) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        if (!TaskStatus.RUNNING.equals(TaskStatus.fromCode(task.getStatus()))) {
            throw new BusinessException("TASK_NOT_RUNNING", "Only RUNNING task can be preempted", 400);
        }

        Long gpuId = task.getGpuId();
        if (reason != null && !reason.isBlank()) {
            GpuTask update = new GpuTask();
            update.setId(taskId);
            update.setErrorMessage(reason);
            taskMapper.updateById(update);
        }
        transition(taskId, TaskStatus.QUEUED, null, operatorId);
        if (gpuId != null) {
            gpuMapper.tryMarkIdle(gpuId, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode());
        }
        return getTask(taskId);
    }

    /**
     * 强制将运行任务置为失败（用于运维兜底）
     */
    @Transactional
    public TaskResponse forceFailTask(Long taskId, Long operatorId, String reason) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        if (status != TaskStatus.RUNNING) {
            throw new BusinessException("TASK_NOT_RUNNING", "Only RUNNING task can be force-failed", 400);
        }

        if (reason != null && !reason.isBlank()) {
            GpuTask update = new GpuTask();
            update.setId(taskId);
            update.setErrorMessage(reason);
            taskMapper.updateById(update);
        }

        Long gpuId = task.getGpuId();
        transition(taskId, TaskStatus.FAILED, gpuId, operatorId);
        if (gpuId != null) {
            gpuMapper.tryMarkIdle(gpuId, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode());
        }
        return getTask(taskId);
    }

    /**
     * 清空排队中的任务，将其批量置为取消状态。
     */
    @Transactional
    public int drainQueuedTasks(Long operatorId, String reason) {
        List<GpuTask> queuedTasks = taskMapper.selectList(
                new LambdaQueryWrapper<GpuTask>()
                        .eq(GpuTask::getStatus, TaskStatus.QUEUED.getCode()));

        int drained = 0;
        for (GpuTask queuedTask : queuedTasks) {
            if (reason != null && !reason.isBlank()) {
                GpuTask update = new GpuTask();
                update.setId(queuedTask.getId());
                update.setErrorMessage(reason);
                taskMapper.updateById(update);
            }
            transition(queuedTask.getId(), TaskStatus.CANCELLED, null, operatorId);
            drained++;
        }
        return drained;
    }

    private void validateSubmissionPolicy(SubmitTaskRequest request, Long userId, List<String> roleCodes) {
        boolean approverRole = hasApprovalRole(roleCodes);
        if (!approverRole) {
            Long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<GpuTask>()
                    .eq(GpuTask::getUserId, userId)
                    .in(GpuTask::getStatus,
                            TaskStatus.PENDING.getCode(),
                            TaskStatus.PENDING_APPROVAL.getCode(),
                            TaskStatus.QUEUED.getCode(),
                            TaskStatus.RUNNING.getCode()));

            if (activeTaskCount != null && activeTaskCount >= submissionPolicy.getMaxActiveTasksPerUser()) {
                throw new BusinessException(
                        "TASK_ACTIVE_LIMIT_EXCEEDED",
                        "Active task limit exceeded, please wait for running tasks to finish",
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

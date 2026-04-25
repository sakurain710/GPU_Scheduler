package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.config.TaskSubmissionPolicyConfig;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskAdminListItem;
import com.sakurain.gpuscheduler.dto.task.TaskExecutionLogItem;
import com.sakurain.gpuscheduler.dto.task.TaskExecutionLogResponse;
import com.sakurain.gpuscheduler.dto.task.TaskExecutionLogTaskSummary;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskLogEvent;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler;
import com.sakurain.gpuscheduler.scheduler.TaskExecutionSimulator;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import com.sakurain.gpuscheduler.util.PaginationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GPU任务服务
 */
@Slf4j
@Service
public class GpuTaskService {

    private final GpuTaskMapper taskMapper;
    private final GpuMapper gpuMapper;
    private final GpuTaskLogMapper taskLogMapper;
    private final UserMapper userMapper;
    private final TaskStateMachine stateMachine;
    private final TaskPriorityQueue priorityQueue;
    private final TaskAgingScheduler agingScheduler;
    private final TaskExecutionSimulator taskExecutionSimulator;
    private final TaskSubmissionPolicyConfig submissionPolicy;
    private final TaskNotificationService taskNotificationService;

    public GpuTaskService(GpuTaskMapper taskMapper,
                          GpuMapper gpuMapper,
                          GpuTaskLogMapper taskLogMapper,
                          UserMapper userMapper,
                          TaskStateMachine stateMachine,
                          TaskPriorityQueue priorityQueue,
                          TaskAgingScheduler agingScheduler,
                          TaskExecutionSimulator taskExecutionSimulator,
                          TaskSubmissionPolicyConfig submissionPolicy,
                          TaskNotificationService taskNotificationService) {
        this.taskMapper = taskMapper;
        this.gpuMapper = gpuMapper;
        this.taskLogMapper = taskLogMapper;
        this.userMapper = userMapper;
        this.stateMachine = stateMachine;
        this.priorityQueue = priorityQueue;
        this.agingScheduler = agingScheduler;
        this.taskExecutionSimulator = taskExecutionSimulator;
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
                .applyReason(request.getApplyReason())
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
        transition(taskId, target, gpuId, operatorId, null, null);
    }

    @Transactional
    public void transition(Long taskId,
                           TaskStatus target,
                           Long gpuId,
                           Long operatorId,
                           TaskLogEvent event,
                           String detail) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }

        TaskStatus from = TaskStatus.fromCode(task.getStatus());
        stateMachine.validateTransition(from, target);

        if (from == TaskStatus.RUNNING && target != TaskStatus.RUNNING) {
            taskExecutionSimulator.cancelTask(taskId);
        }

        task.setStatus(target.getCode());
        if (target == TaskStatus.QUEUED) {
            task.setEnqueueAt(LocalDateTime.now());
            // 支持抢占后重入队：清理运行态字段
            task.setGpuId(null);
            task.setDispatchedAt(null);
            task.setEstimatedFinishAt(null);
        }
        if (target == TaskStatus.RUNNING && gpuId != null) {
            task.setGpuId(gpuId);
            task.setDispatchedAt(LocalDateTime.now());
        }
        if (target.isTerminal()) {
            task.setFinishedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);

        if (target == TaskStatus.QUEUED) {
            double effectivePriority = agingScheduler.calculateEffectivePriority(task);
            priorityQueue.enqueue(taskId, effectivePriority);
        } else if (from == TaskStatus.QUEUED) {
            priorityQueue.remove(taskId);
        }

        writeAudit(taskId, gpuId, from, target, operatorId, event, detail);
        taskNotificationService.notifyTaskStatus(
                taskId,
                task.getUserId(),
                from,
                target,
                resolveNotificationMessage(task, target)
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

    public TaskExecutionLogResponse getTaskExecutionLogs(Long taskId, Long requesterId, List<String> roleCodes) {
        TaskResponse task = getTask(taskId, requesterId, roleCodes);

        List<GpuTaskLog> logs = taskLogMapper.selectList(new LambdaQueryWrapper<GpuTaskLog>()
                .eq(GpuTaskLog::getTaskId, taskId)
                .orderByAsc(GpuTaskLog::getCreatedAt, GpuTaskLog::getId));

        List<GpuTaskLog> sortedLogs = logs.stream()
                .sorted(Comparator
                        .comparing(GpuTaskLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GpuTaskLog::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Set<Long> userIds = new LinkedHashSet<>();
        if (task.getUserId() != null) {
            userIds.add(task.getUserId());
        }
        sortedLogs.stream()
                .map(GpuTaskLog::getOperatorId)
                .filter(id -> id != null && id > 0)
                .forEach(userIds::add);
        Map<Long, String> userDisplayNameMap = loadUserDisplayNameMap(userIds);

        TaskExecutionLogTaskSummary taskSummary = TaskExecutionLogTaskSummary.builder()
                .id(task.getId())
                .gpuId(task.getGpuId())
                .gpuLabel(resolveGpuLabel(task.getGpuId()))
                .operatorId(task.getUserId())
                .operatorName(userDisplayNameMap.get(task.getUserId()))
                .build();

        List<TaskExecutionLogItem> items = sortedLogs.stream()
                .map(log -> toTaskExecutionLogItem(log, userDisplayNameMap))
                .toList();

        return TaskExecutionLogResponse.builder()
                .task(taskSummary)
                .logs(items)
                .build();
    }

    @Transactional
    public void cancelTask(Long taskId, Long requesterId, List<String> roleCodes) {
        cancelTask(taskId, requesterId, roleCodes, null);
    }

    @Transactional
    public void cancelTask(Long taskId, Long requesterId, List<String> roleCodes, String reason) {
        GpuTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        validateTaskOwnerOrApprover(task, requesterId, roleCodes);
        if (reason != null && !reason.isBlank()) {
            GpuTask update = new GpuTask();
            update.setId(taskId);
            update.setCancelReason(reason);
            taskMapper.updateById(update);
        }
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

    public IPage<TaskAdminListItem> listAdminTasks(Integer page,
                                                   Integer size,
                                                   String taskType,
                                                   Integer status,
                                                   String sortDir) {
        Page<GpuTask> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 10, 200)
        );
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(GpuTask::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(GpuTask::getStatus, status);
        }
        boolean asc = sortDir != null && "asc".equalsIgnoreCase(sortDir);
        wrapper.orderBy(true, asc, GpuTask::getId);
        return taskMapper.selectPage(pageParam, wrapper).convert(this::toAdminListItem);
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

    /**
     * 全局任务流查询（监控大屏）
     */
    public IPage<TaskResponse> listGlobalTasks(Integer page,
                                               Integer size,
                                               Integer status,
                                               Boolean activeOnly,
                                               String sortBy,
                                               String sortDir) {
        Page<GpuTask> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 20, 200)
        );
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(GpuTask::getStatus, status);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            wrapper.in(GpuTask::getStatus,
                    TaskStatus.QUEUED.getCode(),
                    TaskStatus.RUNNING.getCode());
        }
        applyTaskSort(wrapper, sortBy, sortDir, true);
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
            case "updatedAt" -> wrapper.orderBy(true, asc, GpuTask::getUpdatedAt);
            default -> wrapper.orderBy(true, asc, GpuTask::getCreatedAt);
        }
    }

    @Transactional
    public TaskResponse approveTask(Long taskId, Long approverId) {
        GpuTask update = new GpuTask();
        update.setId(taskId);
        update.setReviewerId(approverId);
        update.setReviewAt(LocalDateTime.now());
        taskMapper.updateById(update);
        transition(taskId, TaskStatus.QUEUED, null, approverId, TaskLogEvent.APPROVED, null);
        return getTask(taskId);
    }

    @Transactional
    public TaskResponse rejectTask(Long taskId, Long approverId, String reason) {
        GpuTask update = new GpuTask();
        update.setId(taskId);
        update.setReviewerId(approverId);
        update.setReviewAt(LocalDateTime.now());
        update.setRejectReason(reason);
        taskMapper.updateById(update);
        transition(taskId, TaskStatus.REJECTED, null, approverId);
        return getTask(taskId);
    }

    @Transactional
    public List<TaskResponse> batchApproveTasks(List<Long> taskIds, Long approverId) {
        List<Long> normalizedTaskIds = normalizeTaskIds(taskIds);
        List<TaskResponse> responses = new ArrayList<>(normalizedTaskIds.size());
        for (Long taskId : normalizedTaskIds) {
            responses.add(approveTask(taskId, approverId));
        }
        return responses;
    }

    @Transactional
    public List<TaskResponse> batchRejectTasks(List<Long> taskIds, Long approverId, String reason) {
        List<Long> normalizedTaskIds = normalizeTaskIds(taskIds);
        List<TaskResponse> responses = new ArrayList<>(normalizedTaskIds.size());
        for (Long taskId : normalizedTaskIds) {
            responses.add(rejectTask(taskId, approverId, reason));
        }
        return responses;
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
            update.setCancelReason(reason);
            taskMapper.updateById(update);
        }
        transition(taskId, TaskStatus.QUEUED, null, operatorId, TaskLogEvent.PREEMPTED, reason);
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
        transition(taskId, TaskStatus.FAILED, gpuId, operatorId, TaskLogEvent.FORCE_FAILED, reason);
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
                update.setCancelReason(reason);
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
        if (!hasApprovalRole && (task.getUserId() == null || !task.getUserId().equals(requesterId))) {
            throw new BusinessException("TASK_FORBIDDEN", "No permission to access this task", 403);
        }
    }

    private List<Long> normalizeTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BusinessException("TASK_BATCH_EMPTY", "taskIds cannot be empty", 400);
        }
        if (taskIds.size() > 100) {
            throw new BusinessException("TASK_BATCH_LIMIT_EXCEEDED", "batch size exceeds 100", 400);
        }
        if (taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("TASK_BATCH_ID_INVALID", "taskId must be positive", 400);
        }
        return List.copyOf(new LinkedHashSet<>(taskIds));
    }

    public void appendAudit(Long taskId,
                            Long gpuId,
                            TaskLogEvent event,
                            TaskStatus oldStatus,
                            TaskStatus newStatus,
                            Long operatorId,
                            String detail) {
        writeAudit(taskId, gpuId, oldStatus, newStatus, operatorId, event, detail);
    }

    private void writeAudit(Long taskId, Long gpuId, TaskStatus oldStatus, TaskStatus newStatus, Long operatorId) {
        writeAudit(taskId, gpuId, oldStatus, newStatus, operatorId, null, null);
    }

    private void writeAudit(Long taskId,
                            Long gpuId,
                            TaskStatus oldStatus,
                            TaskStatus newStatus,
                            Long operatorId,
                            TaskLogEvent event,
                            String detail) {
        GpuTaskLog logEntry = GpuTaskLog.builder()
                .taskId(taskId)
                .gpuId(gpuId)
                .event(event != null ? event.getCode() : stateMachine.resolveEvent(newStatus))
                .oldStatus(oldStatus == null ? null : oldStatus.getCode())
                .newStatus(newStatus == null ? null : newStatus.getCode())
                .detail(detail)
                .operatorId(operatorId)
                .build();
        taskLogMapper.insert(logEntry);
    }

    private TaskExecutionLogItem toTaskExecutionLogItem(GpuTaskLog log, Map<Long, String> userDisplayNameMap) {
        return TaskExecutionLogItem.builder()
                .event(log.getEvent())
                .oldStatus(log.getOldStatus())
                .oldStatusLabel(resolveStatusLabel(log.getOldStatus()))
                .newStatus(log.getNewStatus())
                .newStatusLabel(resolveStatusLabel(log.getNewStatus()))
                .gpuId(log.getGpuId())
                .operatorId(log.getOperatorId())
                .operatorName(userDisplayNameMap.get(log.getOperatorId()))
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private Map<Long, String> loadUserDisplayNameMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> displayNameByUserId = new HashMap<>(users.size());
        for (User user : users) {
            displayNameByUserId.put(user.getId(), resolveUserDisplayName(user));
        }
        return displayNameByUserId;
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String resolveStatusLabel(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        try {
            return TaskStatus.fromCode(statusCode).getLabel();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveGpuLabel(Long gpuId) {
        if (gpuId == null) {
            return null;
        }
        Gpu gpu = gpuMapper.selectById(gpuId);
        return gpu == null ? null : gpu.getName();
    }

    private TaskAdminListItem toAdminListItem(GpuTask task) {
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        return TaskAdminListItem.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .taskType(task.getTaskType())
                .status(status.getCode())
                .statusLabel(status.getLabel())
                .build();
    }

    private TaskResponse toResponse(GpuTask task) {
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        return TaskResponse.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .gpuId(task.getGpuId())
                .title(task.getTitle())
                .description(task.getDescription())
                .applyReason(task.getApplyReason())
                .taskType(task.getTaskType())
                .minMemoryGb(task.getMinMemoryGb())
                .computeUnitsGflop(task.getComputeUnitsGflop())
                .basePriority(task.getBasePriority())
                .status(status.getCode())
                .statusLabel(status.getLabel())
                .estimatedSeconds(task.getEstimatedSeconds())
                .actualSeconds(task.getActualSeconds())
                .errorMessage(task.getErrorMessage())
                .reviewerId(task.getReviewerId())
                .reviewAt(task.getReviewAt())
                .rejectReason(task.getRejectReason())
                .cancelReason(task.getCancelReason())
                .enqueueAt(task.getEnqueueAt())
                .dispatchedAt(task.getDispatchedAt())
                .estimatedFinishAt(task.getEstimatedFinishAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private String resolveNotificationMessage(GpuTask task, TaskStatus target) {
        if (target == TaskStatus.REJECTED) {
            return task.getRejectReason();
        }
        if (target == TaskStatus.CANCELLED) {
            return task.getCancelReason();
        }
        if (target == TaskStatus.FAILED) {
            return task.getErrorMessage();
        }
        return null;
    }
}

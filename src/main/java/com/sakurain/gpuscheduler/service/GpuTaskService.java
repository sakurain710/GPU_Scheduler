package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler agingScheduler;

    public GpuTaskService(GpuTaskMapper taskMapper,
                          GpuTaskLogMapper taskLogMapper,
                          TaskStateMachine stateMachine,
                          TaskPriorityQueue priorityQueue,
                          com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler agingScheduler) {
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.stateMachine = stateMachine;
        this.priorityQueue = priorityQueue;
        this.agingScheduler = agingScheduler;
    }

    /**
     * 提交任务 — 持久化为PENDING，然后立即转换为QUEUED并入队Redis
     */
    @Transactional
    public TaskResponse submitTask(SubmitTaskRequest request, Long userId) {
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

        log.info("任务状态转换: taskId={}, {} → {}", taskId, from.getLabel(), target.getLabel());
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

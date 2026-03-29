package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.config.TaskPreemptionPolicyConfig;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 自动抢占服务
 */
@Slf4j
@Service
public class TaskPreemptionService {

    private final TaskPreemptionPolicyConfig preemptionPolicy;
    private final GpuTaskMapper gpuTaskMapper;
    private final GpuMapper gpuMapper;
    private final GpuTaskService gpuTaskService;

    public TaskPreemptionService(TaskPreemptionPolicyConfig preemptionPolicy,
                                 GpuTaskMapper gpuTaskMapper,
                                 GpuMapper gpuMapper,
                                 GpuTaskService gpuTaskService) {
        this.preemptionPolicy = preemptionPolicy;
        this.gpuTaskMapper = gpuTaskMapper;
        this.gpuMapper = gpuMapper;
        this.gpuTaskService = gpuTaskService;
    }

    public boolean tryPreemptFor(GpuTask waitingTask) {
        if (!preemptionPolicy.isEnabled()) {
            return false;
        }
        if (waitingTask.getBasePriority() == null || waitingTask.getBasePriority() < preemptionPolicy.getTriggerPriority()) {
            return false;
        }

        List<GpuTask> runningTasks = gpuTaskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
                .isNotNull(GpuTask::getGpuId));

        BigDecimal requiredMemory = waitingTask.getMinMemoryGb() != null ? waitingTask.getMinMemoryGb() : BigDecimal.ZERO;
        int waitingPriority = waitingTask.getBasePriority();

        GpuTask candidate = runningTasks.stream()
                .filter(t -> t.getBasePriority() != null)
                .filter(t -> waitingPriority - t.getBasePriority() >= preemptionPolicy.getMinPriorityGap())
                .filter(t -> canGpuFitTask(t.getGpuId(), requiredMemory))
                .min(Comparator.comparing(GpuTask::getBasePriority)
                        .thenComparing(GpuTask::getDispatchedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (candidate == null) {
            return false;
        }

        try {
            gpuTaskService.preemptTask(candidate.getId(), null,
                    "Auto-preempted for high-priority task " + waitingTask.getId());
            log.info("自动抢占成功: preemptedTaskId={}, waitingTaskId={}", candidate.getId(), waitingTask.getId());
            return true;
        } catch (Exception ex) {
            log.warn("自动抢占失败: preemptedTaskId={}, waitingTaskId={}, err={}",
                    candidate.getId(), waitingTask.getId(), ex.getMessage());
            return false;
        }
    }

    private boolean canGpuFitTask(Long gpuId, BigDecimal requiredMemory) {
        if (gpuId == null) {
            return false;
        }
        Gpu gpu = gpuMapper.selectById(gpuId);
        return gpu != null && gpu.getMemoryGb() != null && gpu.getMemoryGb().compareTo(requiredMemory) >= 0;
    }
}


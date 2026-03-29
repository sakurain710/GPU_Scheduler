package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.config.TaskPreemptionPolicyConfig;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final RedisTemplate<String, String> redisTemplate;

    public TaskPreemptionService(TaskPreemptionPolicyConfig preemptionPolicy,
                                 GpuTaskMapper gpuTaskMapper,
                                 GpuMapper gpuMapper,
                                 GpuTaskService gpuTaskService,
                                 RedisTemplate<String, String> redisTemplate) {
        this.preemptionPolicy = preemptionPolicy;
        this.gpuTaskMapper = gpuTaskMapper;
        this.gpuMapper = gpuMapper;
        this.gpuTaskService = gpuTaskService;
        this.redisTemplate = redisTemplate;
    }

    public boolean tryPreemptFor(GpuTask waitingTask) {
        if (!preemptionPolicy.isEnabled()) {
            return false;
        }
        if (waitingTask.getBasePriority() == null || waitingTask.getBasePriority() < preemptionPolicy.getTriggerPriority()) {
            return false;
        }
        if (!allowPreemptionByRateLimit()) {
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
                .filter(t -> !isTaskCoolingDown(t.getId()))
                .min(Comparator.comparing(GpuTask::getBasePriority)
                        .thenComparing(GpuTask::getDispatchedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (candidate == null) {
            return false;
        }

        try {
            gpuTaskService.preemptTask(candidate.getId(), null,
                    "Auto-preempted for high-priority task " + waitingTask.getId());
            markTaskCooldown(candidate.getId());
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

    private boolean isTaskCoolingDown(Long taskId) {
        if (taskId == null || preemptionPolicy.getCooldownSeconds() <= 0) {
            return false;
        }
        String key = "gpu:task:preempt:cooldown:" + taskId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markTaskCooldown(Long taskId) {
        if (taskId == null || preemptionPolicy.getCooldownSeconds() <= 0) {
            return;
        }
        String key = "gpu:task:preempt:cooldown:" + taskId;
        redisTemplate.opsForValue().set(key, "1", preemptionPolicy.getCooldownSeconds(), TimeUnit.SECONDS);
    }

    private boolean allowPreemptionByRateLimit() {
        int limit = preemptionPolicy.getMaxPreemptionsPerMinute();
        if (limit <= 0) {
            return true;
        }
        String minute = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String key = "gpu:task:preempt:counter:" + minute;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 120, TimeUnit.SECONDS);
        return count == null || count <= limit;
    }
}

package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.config.WorkerHeartbeatPolicyConfig;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GPU worker 心跳追踪与 stale 检测。
 */
@Slf4j
@Service
public class WorkerHeartbeatService {

    private static final String KEY_PREFIX = "gpu:worker:heartbeat:";

    private final RedisTemplate<String, String> redisTemplate;
    private final WorkerHeartbeatPolicyConfig policy;
    private final GpuMapper gpuMapper;
    private final GpuTaskMapper gpuTaskMapper;
    private final GpuTaskService gpuTaskService;

    public WorkerHeartbeatService(RedisTemplate<String, String> redisTemplate,
                                  WorkerHeartbeatPolicyConfig policy,
                                  GpuMapper gpuMapper,
                                  GpuTaskMapper gpuTaskMapper,
                                  GpuTaskService gpuTaskService) {
        this.redisTemplate = redisTemplate;
        this.policy = policy;
        this.gpuMapper = gpuMapper;
        this.gpuTaskMapper = gpuTaskMapper;
        this.gpuTaskService = gpuTaskService;
    }

    public void beat(Long gpuId) {
        Gpu gpu = gpuMapper.selectById(gpuId);
        if (gpu == null) {
            throw new ResourceNotFoundException("GPU not found: " + gpuId);
        }
        long nowEpoch = Instant.now().getEpochSecond();
        String key = heartbeatKey(gpuId);
        long ttl = Math.max(policy.getStaleThresholdSeconds() * 3L, 30L);
        redisTemplate.opsForValue().set(key, Long.toString(nowEpoch), ttl, TimeUnit.SECONDS);

        // OFFLINE worker恢复心跳后，允许自动恢复为IDLE。
        gpuMapper.tryMarkIdleFromOffline(
                gpuId,
                GpuStatus.OFFLINE.getCode(),
                GpuStatus.IDLE.getCode()
        );
    }

    public Long heartbeatAgeSeconds(Long gpuId) {
        String value = redisTemplate.opsForValue().get(heartbeatKey(gpuId));
        if (value == null) {
            return null;
        }
        try {
            long heartbeatAt = Long.parseLong(value);
            long age = Instant.now().getEpochSecond() - heartbeatAt;
            return Math.max(age, 0L);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Scheduled(fixedDelayString = "${worker-heartbeat.scan-interval-ms:15000}")
    public void scanAndRecoverStaleWorkers() {
        if (!policy.isEnabled()) {
            return;
        }

        List<Gpu> busyGpus = gpuMapper.selectList(
                new LambdaQueryWrapper<Gpu>().eq(Gpu::getStatus, GpuStatus.BUSY.getCode())
        );
        long threshold = Math.max(policy.getStaleThresholdSeconds(), 10);

        for (Gpu gpu : busyGpus) {
            Long age = heartbeatAgeSeconds(gpu.getId());
            if (age != null && age <= threshold) {
                continue;
            }
            handleStaleGpu(gpu.getId(), age);
        }
    }

    private void handleStaleGpu(Long gpuId, Long heartbeatAge) {
        GpuTask runningTask = gpuTaskMapper.selectOne(
                new LambdaQueryWrapper<GpuTask>()
                        .eq(GpuTask::getGpuId, gpuId)
                        .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
                        .orderByDesc(GpuTask::getDispatchedAt)
                        .last("LIMIT 1")
        );

        if (runningTask != null) {
            String reason = "Worker heartbeat stale"
                    + (heartbeatAge == null ? "" : ", ageSeconds=" + heartbeatAge);
            try {
                GpuTask update = new GpuTask();
                update.setId(runningTask.getId());
                update.setErrorMessage(reason);
                gpuTaskMapper.updateById(update);
                gpuTaskService.transition(runningTask.getId(), TaskStatus.QUEUED, null, null);
            } catch (Exception ex) {
                log.warn("stale worker recovery task transition failed: gpuId={}, taskId={}, err={}",
                        gpuId, runningTask.getId(), ex.getMessage());
            }
        }

        gpuMapper.tryMarkOfflineFromBusy(
                gpuId,
                GpuStatus.BUSY.getCode(),
                GpuStatus.OFFLINE.getCode()
        );
        log.warn("stale worker detected and marked offline: gpuId={}, heartbeatAgeSeconds={}", gpuId, heartbeatAge);
    }

    private String heartbeatKey(Long gpuId) {
        return KEY_PREFIX + gpuId;
    }
}

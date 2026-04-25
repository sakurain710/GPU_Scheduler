package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.service.TaskPreemptionService;
import com.sakurain.gpuscheduler.util.RedisLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 任务调度器
 */
@Slf4j
@Component
public class TaskDispatcher {

    private static final String LOCK_KEY = "dispatcher:lock";
    private static final String BACKOFF_FAILURES_KEY = "gpu:dispatcher:backoff:consecutive-failures";
    private static final String BACKOFF_REMAINING_KEY = "gpu:dispatcher:backoff:remaining-rounds";
    private static final String BACKOFF_CURRENT_KEY = "gpu:dispatcher:backoff:current-rounds";
    private static final String CONSUME_BACKOFF_SCRIPT =
            "local remaining = tonumber(redis.call('get', KEYS[1]) or '0') " +
            "if remaining > 0 then " +
            "  redis.call('decr', KEYS[1]) " +
            "  return 1 " +
            "end " +
            "return 0";
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int INITIAL_BACKOFF_ROUNDS = 1;
    private static final int MAX_BACKOFF_ROUNDS = 32;

    private final TaskPriorityQueue priorityQueue;
    private final GpuAllocator gpuAllocator;
    private final GpuTaskMapper taskMapper;
    private final RedisLockService lockService;
    private final TaskAssignmentService assignmentService;
    private final TaskAgingScheduler agingScheduler;
    private final TaskPreemptionService taskPreemptionService;
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> consumeBackoffScript;

    private volatile boolean paused = false;
    @Value("${scheduler.scheduled-jobs-enabled:true}")
    private boolean scheduledJobsEnabled;

    public TaskDispatcher(TaskPriorityQueue priorityQueue,
                          GpuAllocator gpuAllocator,
                          GpuTaskMapper taskMapper,
                          RedisLockService lockService,
                          TaskAssignmentService assignmentService,
                          TaskAgingScheduler agingScheduler,
                          TaskPreemptionService taskPreemptionService,
                          RedisTemplate<String, String> redisTemplate) {
        this.priorityQueue = priorityQueue;
        this.gpuAllocator = gpuAllocator;
        this.taskMapper = taskMapper;
        this.lockService = lockService;
        this.assignmentService = assignmentService;
        this.agingScheduler = agingScheduler;
        this.taskPreemptionService = taskPreemptionService;
        this.redisTemplate = redisTemplate;
        this.consumeBackoffScript = new DefaultRedisScript<>(CONSUME_BACKOFF_SCRIPT, Long.class);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        if (!scheduledJobsEnabled || paused) {
            return;
        }
        if (consumeBackoffRound()) {
            return;
        }

        String lockValue = UUID.randomUUID().toString();
        if (!lockService.tryLock(LOCK_KEY, lockValue, 10, TimeUnit.SECONDS)) {
            return;
        }

        try {
            while (true) {
                Long taskId = priorityQueue.dequeue();
                if (taskId == null) {
                    break;
                }

                GpuTask task = taskMapper.selectById(taskId);
                if (task == null || task.getStatus() != TaskStatus.QUEUED.getCode()) {
                    continue;
                }

                Optional<Gpu> gpuOpt = gpuAllocator.allocate(task);
                if (gpuOpt.isEmpty()) {
                    boolean preempted = taskPreemptionService.tryPreemptFor(task);
                    requeueWithEffectivePriority(task);
                    if (!preempted) {
                        onFailure();
                        break;
                    } else {
                        resetBackoff();
                        continue;
                    }
                }

                try {
                    assignmentService.assign(task, gpuOpt.get());
                    resetBackoff();
                } catch (Exception e) {
                    log.error("分配GPU失败: taskId={}, gpuId={}", taskId, gpuOpt.get().getId(), e);
                    requeueWithEffectivePriority(task);
                    onFailure();
                    break;
                }
            }
        } catch (Exception e) {
            log.error("调度循环异常", e);
        } finally {
            lockService.unlock(LOCK_KEY, lockValue);
        }
    }

    public void dispatchOnce() {
        dispatch();
    }

    /**
     * 暂停调度循环
     */
    public void pauseDispatch() {
        this.paused = true;
    }

    /**
     * 恢复调度循环
     */
    public void resumeDispatch() {
        this.paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    private void requeueWithEffectivePriority(GpuTask task) {
        double effectivePriority = agingScheduler.calculateEffectivePriority(task);
        priorityQueue.enqueue(task.getId(), effectivePriority);
    }

    private void onFailure() {
        Long failures = redisTemplate.opsForValue().increment(BACKOFF_FAILURES_KEY);
        long currentFailures = failures == null ? 1L : failures;
        if (currentFailures >= MAX_CONSECUTIVE_FAILURES) {
            int currentRounds = readInt(BACKOFF_CURRENT_KEY, INITIAL_BACKOFF_ROUNDS);
            int nextRounds = Math.min(currentRounds * 2, MAX_BACKOFF_ROUNDS);
            redisTemplate.opsForValue().set(BACKOFF_CURRENT_KEY, Integer.toString(nextRounds));
            redisTemplate.opsForValue().set(BACKOFF_REMAINING_KEY, Integer.toString(nextRounds));
            redisTemplate.delete(BACKOFF_FAILURES_KEY);
        }
    }

    private boolean consumeBackoffRound() {
        Long consumed = redisTemplate.execute(consumeBackoffScript, Collections.singletonList(BACKOFF_REMAINING_KEY));
        return consumed != null && consumed > 0;
    }

    private void resetBackoff() {
        redisTemplate.delete(BACKOFF_FAILURES_KEY);
        redisTemplate.delete(BACKOFF_REMAINING_KEY);
        redisTemplate.opsForValue().set(BACKOFF_CURRENT_KEY, Integer.toString(INITIAL_BACKOFF_ROUNDS));
    }

    private int readInt(String key, int defaultValue) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}

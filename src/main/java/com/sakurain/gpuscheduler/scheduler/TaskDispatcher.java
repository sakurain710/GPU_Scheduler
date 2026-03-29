package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.util.RedisLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int INITIAL_BACKOFF_ROUNDS = 1;
    private static final int MAX_BACKOFF_ROUNDS = 32;

    private final TaskPriorityQueue priorityQueue;
    private final GpuAllocator gpuAllocator;
    private final GpuTaskMapper taskMapper;
    private final RedisLockService lockService;
    private final TaskAssignmentService assignmentService;
    private final TaskAgingScheduler agingScheduler;

    private int consecutiveFailures = 0;
    private int backoffRoundsRemaining = 0;
    private int currentBackoffRounds = INITIAL_BACKOFF_ROUNDS;
    private volatile boolean paused = false;

    public TaskDispatcher(TaskPriorityQueue priorityQueue,
                          GpuAllocator gpuAllocator,
                          GpuTaskMapper taskMapper,
                          RedisLockService lockService,
                          TaskAssignmentService assignmentService,
                          TaskAgingScheduler agingScheduler) {
        this.priorityQueue = priorityQueue;
        this.gpuAllocator = gpuAllocator;
        this.taskMapper = taskMapper;
        this.lockService = lockService;
        this.assignmentService = assignmentService;
        this.agingScheduler = agingScheduler;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        if (paused) {
            return;
        }
        if (backoffRoundsRemaining > 0) {
            backoffRoundsRemaining--;
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
                    requeueWithEffectivePriority(task);
                    onFailure();
                    break;
                }

                try {
                    assignmentService.assign(task, gpuOpt.get());
                    consecutiveFailures = 0;
                    currentBackoffRounds = INITIAL_BACKOFF_ROUNDS;
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
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            currentBackoffRounds = Math.min(currentBackoffRounds * 2, MAX_BACKOFF_ROUNDS);
            backoffRoundsRemaining = currentBackoffRounds;
            consecutiveFailures = 0;
        }
    }
}

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
 * 任务调度器 — 持续轮询Redis队列并分配GPU
 * <p>
 * 调度循环：
 * 1. 从Redis队列出队最高优先级任务
 * 2. 使用BestFit算法查找合适的GPU
 * 3. 分配GPU并转换任务状态 QUEUED→RUNNING
 * 4. 更新GPU状态为BUSY
 * <p>
 * 熔断器：连续分配失败达到阈值后，指数退避跳过若干轮调度，
 * 避免在无可用GPU时持续空转。退避不使用 Thread.sleep()，
 * 而是记录"跳过轮次"，由 @Scheduled 的固定延迟自然提供间隔。
 */
@Slf4j
@Component
public class TaskDispatcher {

    private static final String LOCK_KEY = "dispatcher:lock";
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    /** 初始退避轮次（每次触发熔断后翻倍，最大 MAX_BACKOFF_ROUNDS 轮） */
    private static final int INITIAL_BACKOFF_ROUNDS = 1;
    private static final int MAX_BACKOFF_ROUNDS = 32;

    private final TaskPriorityQueue priorityQueue;
    private final GpuAllocator gpuAllocator;
    private final GpuTaskMapper taskMapper;
    private final RedisLockService lockService;
    private final TaskAssignmentService assignmentService;

    private int consecutiveFailures = 0;
    /** 指数退避：剩余需跳过的调度轮次 */
    private int backoffRoundsRemaining = 0;
    /** 当前退避步长（触发熔断时翻倍） */
    private int currentBackoffRounds = INITIAL_BACKOFF_ROUNDS;

    public TaskDispatcher(TaskPriorityQueue priorityQueue,
                          GpuAllocator gpuAllocator,
                          GpuTaskMapper taskMapper,
                          RedisLockService lockService,
                          TaskAssignmentService assignmentService) {
        this.priorityQueue = priorityQueue;
        this.gpuAllocator = gpuAllocator;
        this.taskMapper = taskMapper;
        this.lockService = lockService;
        this.assignmentService = assignmentService;
    }

    /**
     * 调度循环 — 每5秒执行一次
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        // 指数退避：跳过本轮
        if (backoffRoundsRemaining > 0) {
            backoffRoundsRemaining--;
            log.debug("熔断退避中，剩余跳过轮次: {}", backoffRoundsRemaining);
            return;
        }

        String lockValue = UUID.randomUUID().toString();
        if (!lockService.tryLock(LOCK_KEY, lockValue, 10, TimeUnit.SECONDS)) {
            log.debug("无法获取调度锁，跳过本次调度");
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
                    log.warn("任务{}不存在或状态已变更", taskId);
                    continue;
                }

                Optional<Gpu> gpuOpt = gpuAllocator.allocate(task);
                if (gpuOpt.isEmpty()) {
                    priorityQueue.enqueue(taskId, task.getBasePriority());
                    log.debug("没有可用GPU满足任务{}的需求，重新入队", taskId);

                    consecutiveFailures++;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        // 触发熔断，指数退避
                        currentBackoffRounds = Math.min(currentBackoffRounds * 2, MAX_BACKOFF_ROUNDS);
                        backoffRoundsRemaining = currentBackoffRounds;
                        log.warn("连续{}次分配失败，触发熔断，退避{}轮", consecutiveFailures, backoffRoundsRemaining);
                        consecutiveFailures = 0;
                    }
                    break;
                }

                Gpu gpu = gpuOpt.get();
                try {
                    assignmentService.assign(task, gpu);
                    log.info("任务{}已分配到GPU[{}] {}", taskId, gpu.getId(), gpu.getName());
                    consecutiveFailures = 0;
                    currentBackoffRounds = INITIAL_BACKOFF_ROUNDS; // 成功后重置退避步长
                } catch (Exception e) {
                    log.error("分配GPU失败: taskId={}, gpuId={}", taskId, gpu.getId(), e);
                    priorityQueue.enqueue(taskId, task.getBasePriority());
                }
            }
        } catch (Exception e) {
            log.error("调度循环异常", e);
        } finally {
            lockService.unlock(LOCK_KEY, lockValue);
        }
    }

    /**
     * 手动触发一次调度（用于测试）
     */
    public void dispatchOnce() {
        dispatch();
    }
}

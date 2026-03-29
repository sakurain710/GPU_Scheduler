package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.config.SchedulerConfig;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 任务老化调度器 — 定期刷新Redis队列中任务的优先级
 * <p>
 * 防止低优先级任务饥饿：等待时间越长，有效优先级越高
 */
@Slf4j
@Component
public class TaskAgingScheduler {

    private final TaskPriorityQueue priorityQueue;
    private final GpuTaskMapper taskMapper;
    private final SchedulerConfig config;

    public TaskAgingScheduler(TaskPriorityQueue priorityQueue,
                              GpuTaskMapper taskMapper,
                              SchedulerConfig config) {
        this.priorityQueue = priorityQueue;
        this.taskMapper = taskMapper;
        this.config = config;
    }

    /**
     * 定期刷新队列中所有任务的score
     * 使用配置的刷新间隔（默认1分钟）
     */
    @Scheduled(fixedDelayString = "${scheduler.refresh-interval-ms:60000}")
    public void refreshTaskPriorities() {
        if (!config.isScheduledJobsEnabled() || !config.isAgingEnabled()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        Set<Long> toRemove = new HashSet<>();

        try {
            // 使用TaskPriorityQueue的refreshScores方法批量更新
            priorityQueue.refreshScores(taskId -> {
                GpuTask task = taskMapper.selectById(taskId);
                if (task == null || task.getStatus() != TaskStatus.QUEUED.getCode()) {
                    // 任务已被删除或状态已变更，标记为待移除
                    toRemove.add(taskId);
                    return 0.0;
                }
                return calculateEffectivePriority(task);
            });

            // 移除无效任务
            toRemove.forEach(priorityQueue::remove);

            int refreshedCount = (int) priorityQueue.size();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("任务优先级刷新完成: 刷新{}个任务, 移除{}个无效任务, 耗时{}ms",
                    refreshedCount, toRemove.size(), elapsed);

        } catch (Exception e) {
            log.error("任务优先级刷新失败", e);
        }
    }

    /**
     * 计算任务的有效优先级
     * 公式: effectivePriority = basePriority + (waitTimeMinutes * ageWeight)
     *
     * @param task 任务实体（必须有enqueueAt和basePriority）
     * @return 有效优先级
     */
    public double calculateEffectivePriority(GpuTask task) {
        if (task.getEnqueueAt() == null) {
            return task.getBasePriority();
        }

        LocalDateTime now = LocalDateTime.now();
        long waitMinutes = Duration.between(task.getEnqueueAt(), now).toMinutes();
        double ageBonus = waitMinutes * config.getAgeWeightPerMinute();

        return task.getBasePriority() + ageBonus;
    }
}

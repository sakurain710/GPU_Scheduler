package com.sakurain.gpuscheduler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.TaskRetryDlqService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务完成监控器
 */
@Slf4j
@Component
public class TaskCompletionMonitor {

    private static final double TIMEOUT_MULTIPLIER = 2.0;
    private static final long MIN_TIMEOUT_MILLIS = 1L;
    private static final BigDecimal MIN_ACTUAL_SECONDS = new BigDecimal("0.0001");

    private final GpuTaskMapper taskMapper;
    private final GpuMapper gpuMapper;
    private final GpuTaskService taskService;
    private final TaskExecutionSimulator simulator;
    private final TaskRetryDlqService retryDlqService;

    @Value("${scheduler.orphan-running-threshold-seconds:120}")
    private long orphanRunningThresholdSeconds;

    public TaskCompletionMonitor(GpuTaskMapper taskMapper,
                                 GpuMapper gpuMapper,
                                 GpuTaskService taskService,
                                 TaskExecutionSimulator simulator,
                                 TaskRetryDlqService retryDlqService) {
        this.taskMapper = taskMapper;
        this.gpuMapper = gpuMapper;
        this.taskService = taskService;
        this.simulator = simulator;
        this.retryDlqService = retryDlqService;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void monitorRunningTasks() {
        List<GpuTask> runningTasks = taskMapper.selectList(
                new LambdaQueryWrapper<GpuTask>()
                        .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
        );

        if (runningTasks.isEmpty()) {
            return;
        }

        for (GpuTask task : runningTasks) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("处理任务{}完成状态异常", task.getId(), e);
            }
        }
    }

    private void processTask(GpuTask task) {
        if (isTimedOut(task)) {
            simulator.cancelTask(task.getId());
            completeTask(task, false, null, "Task execution timed out");
            return;
        }

        if (!simulator.isRunning(task.getId())) {
            TaskExecutionSimulator.TaskExecutionResult result = simulator.getResult(task.getId(), 1);
            if (result != null) {
                completeTask(task, result.isSuccess(), result.getActualSeconds(), result.getErrorMessage());
                return;
            }
            // 重启恢复：若任务不在模拟器中且已运行超过阈值，判定为孤儿任务并回收
            if (isOrphanRunningTask(task)) {
                completeTask(task, false, null, "Orphan RUNNING task recovered after restart");
            }
        }
    }

    private boolean isTimedOut(GpuTask task) {
        if (task.getDispatchedAt() == null || task.getEstimatedSeconds() == null) {
            return false;
        }
        long estimatedMs = task.getEstimatedSeconds().multiply(new BigDecimal("1000")).longValue();
        long timeoutMs = Math.max(MIN_TIMEOUT_MILLIS, (long) (estimatedMs * TIMEOUT_MULTIPLIER));
        long elapsedMs = Duration.between(task.getDispatchedAt(), LocalDateTime.now()).toMillis();
        return elapsedMs > timeoutMs;
    }

    private boolean isOrphanRunningTask(GpuTask task) {
        if (task.getDispatchedAt() == null) {
            return false;
        }
        long elapsedSeconds = Duration.between(task.getDispatchedAt(), LocalDateTime.now()).toSeconds();
        return elapsedSeconds >= orphanRunningThresholdSeconds;
    }

    private void completeTask(GpuTask task, boolean success, BigDecimal actualSeconds, String errorMessage) {
        TaskStatus targetStatus = success ? TaskStatus.COMPLETED : TaskStatus.FAILED;

        GpuTask update = new GpuTask();
        update.setId(task.getId());
        update.setActualSeconds(normalizeActualSeconds(success, actualSeconds, task.getEstimatedSeconds()));
        if (!success && errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        taskMapper.updateById(update);

        taskService.transition(task.getId(), targetStatus, task.getGpuId(), null);

        if (task.getGpuId() != null) {
            releaseGpu(task.getGpuId());
        }

        if (!success) {
            retryDlqService.onTaskFailed(task.getId(), errorMessage);
        }
    }

    private BigDecimal normalizeActualSeconds(boolean success, BigDecimal actualSeconds, BigDecimal estimatedSeconds) {
        if (!success) {
            return null;
        }
        if (actualSeconds != null && actualSeconds.compareTo(BigDecimal.ZERO) > 0) {
            return actualSeconds;
        }
        if (estimatedSeconds != null && estimatedSeconds.compareTo(BigDecimal.ZERO) > 0) {
            return estimatedSeconds.max(MIN_ACTUAL_SECONDS);
        }
        return MIN_ACTUAL_SECONDS;
    }

    private void releaseGpu(Long gpuId) {
        int updated = gpuMapper.tryMarkIdle(
                gpuId,
                GpuStatus.BUSY.getCode(),
                GpuStatus.IDLE.getCode()
        );
        if (updated > 0) {
            log.info("GPU[{}]已释放，状态恢复IDLE", gpuId);
        }
    }
}

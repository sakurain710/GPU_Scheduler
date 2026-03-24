package com.sakurain.gpuscheduler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 任务完成监控器 — 监控运行中的任务，处理完成/失败/超时
 * <p>
 * 职责：
 * 1. 定期扫描RUNNING状态的任务
 * 2. 检查对应Future是否完成
 * 3. 超时任务（超过预估时间2倍）强制失败
 * 4. 完成后释放GPU（状态改回IDLE）
 * 5. 写入实际执行时间和完成审计日志
 */
@Slf4j
@Component
public class TaskCompletionMonitor {

    private static final double TIMEOUT_MULTIPLIER = 2.0;

    private final GpuTaskMapper taskMapper;
    private final GpuMapper gpuMapper;
    private final GpuTaskService taskService;
    private final TaskExecutionSimulator simulator;

    public TaskCompletionMonitor(GpuTaskMapper taskMapper,
                                 GpuMapper gpuMapper,
                                 GpuTaskService taskService,
                                 TaskExecutionSimulator simulator) {
        this.taskMapper = taskMapper;
        this.gpuMapper = gpuMapper;
        this.taskService = taskService;
        this.simulator = simulator;
    }

    /**
     * 每10秒扫描一次运行中的任务
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void monitorRunningTasks() {
        List<GpuTask> runningTasks = taskMapper.selectList(
                new LambdaQueryWrapper<GpuTask>()
                        .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
        );

        if (runningTasks.isEmpty()) {
            return;
        }

        log.debug("监控{}个运行中的任务", runningTasks.size());

        for (GpuTask task : runningTasks) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("处理任务{}完成状态时异常", task.getId(), e);
            }
        }
    }

    private void processTask(GpuTask task) {
        // 检查是否超时
        if (isTimedOut(task)) {
            log.warn("任务{}执行超时，强制失败", task.getId());
            simulator.cancelTask(task.getId());
            completeTask(task, false, null, "Task execution timed out");
            return;
        }

        // 任务已完成（Future.isDone() == true），获取结果
        if (!simulator.isRunning(task.getId())) {
            TaskExecutionSimulator.TaskExecutionResult result = simulator.getResult(task.getId(), 1);
            if (result != null) {
                completeTask(task, result.isSuccess(), result.getActualSeconds(), result.getErrorMessage());
            }
            // result == null means task not in simulator (e.g. after restart) — leave for timeout to handle
        }
    }

    private boolean isTimedOut(GpuTask task) {
        if (task.getDispatchedAt() == null || task.getEstimatedSeconds() == null) {
            return false;
        }
        long estimatedMs = task.getEstimatedSeconds().multiply(new BigDecimal("1000")).longValue();
        long timeoutMs = (long) (estimatedMs * TIMEOUT_MULTIPLIER);
        long elapsedMs = java.time.Duration.between(
                task.getDispatchedAt(),
                java.time.LocalDateTime.now()
        ).toMillis();
        return elapsedMs > timeoutMs;
    }

    /**
     * 完成任务处理：更新状态、释放GPU、记录实际执行时间
     */
    private void completeTask(GpuTask task, boolean success,
                               BigDecimal actualSeconds, String errorMessage) {
        TaskStatus targetStatus = success ? TaskStatus.COMPLETED : TaskStatus.FAILED;

        // 更新实际执行时间和错误信息
        GpuTask update = new GpuTask();
        update.setId(task.getId());
        update.setActualSeconds(actualSeconds);
        if (!success && errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        taskMapper.updateById(update);

        // 状态转换 RUNNING → COMPLETED/FAILED
        taskService.transition(task.getId(), targetStatus, task.getGpuId(), null);

        // 释放GPU：状态改回IDLE
        if (task.getGpuId() != null) {
            releaseGpu(task.getGpuId());
        }

        log.info("任务{}已{}，实际执行{}秒",
                task.getId(),
                success ? "完成" : "失败",
                actualSeconds != null ? actualSeconds : "N/A");
    }

    private void releaseGpu(Long gpuId) {
        Gpu gpu = gpuMapper.selectById(gpuId);
        if (gpu != null && gpu.getStatus() == GpuStatus.BUSY.getCode()) {
            gpu.setStatus(GpuStatus.IDLE.getCode());
            gpuMapper.updateById(gpu);
            log.info("GPU[{}]已释放，状态恢复IDLE", gpuId);
        }
    }
}

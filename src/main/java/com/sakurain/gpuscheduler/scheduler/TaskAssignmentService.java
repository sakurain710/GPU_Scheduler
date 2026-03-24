package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * GPU分配事务服务 — 独立 bean，确保 @Transactional 通过 Spring 代理生效
 */
@Slf4j
@Service
public class TaskAssignmentService {

    private final GpuMapper gpuMapper;
    private final GpuTaskService taskService;
    private final TaskExecutionSimulator simulator;

    public TaskAssignmentService(GpuMapper gpuMapper,
                                 GpuTaskService taskService,
                                 TaskExecutionSimulator simulator) {
        this.gpuMapper = gpuMapper;
        this.taskService = taskService;
        this.simulator = simulator;
    }

    /**
     * 分配GPU给任务（事务方法）
     * 1. 更新GPU状态为BUSY
     * 2. 计算预估完成时间
     * 3. 转换任务状态 QUEUED→RUNNING
     */
    @Transactional
    public void assign(GpuTask task, Gpu gpu) {
        // 1. 更新GPU状态为BUSY
        gpu.setStatus(GpuStatus.BUSY.getCode());
        gpuMapper.updateById(gpu);

        // 2. 计算预估执行时间和完成时间
        BigDecimal estimatedSeconds = calculateEstimatedSeconds(
                task.getComputeUnitsGflop(),
                gpu.getComputingPowerTflops()
        );
        task.setEstimatedSeconds(estimatedSeconds);

        LocalDateTime now = LocalDateTime.now();
        task.setEstimatedFinishAt(now.plusSeconds(estimatedSeconds.longValue()));

        // 3. 转换任务状态 QUEUED→RUNNING，分配GPU
        taskService.transition(task.getId(), TaskStatus.RUNNING, gpu.getId(), null);

        // 4. 事务提交后提交到执行模拟器（避免事务未提交时模拟器已完成）
        final GpuTask taskSnapshot = GpuTask.builder()
                .id(task.getId())
                .gpuId(gpu.getId())
                .estimatedSeconds(estimatedSeconds)
                .dispatchedAt(now)
                .build();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    simulator.submitTask(taskSnapshot);
                } catch (Exception e) {
                    log.error("提交任务{}到执行模拟器失败", taskSnapshot.getId(), e);
                }
            }
        });

        log.info("GPU分配完成: 任务{}分配到GPU[{}], 预估执行{}秒, 预计完成时间{}",
                task.getId(), gpu.getId(), estimatedSeconds, task.getEstimatedFinishAt());
    }

    private BigDecimal calculateEstimatedSeconds(BigDecimal computeUnitsGflop,
                                                  BigDecimal computingPowerTflops) {
        if (computeUnitsGflop == null || computingPowerTflops == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal computingPowerGflops = computingPowerTflops.multiply(new BigDecimal("1000"));
        return computeUnitsGflop.divide(computingPowerGflops, 4, RoundingMode.HALF_UP);
    }
}

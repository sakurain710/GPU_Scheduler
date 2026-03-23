package com.sakurain.gpuscheduler.scheduler;

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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 任务调度器 — 持续轮询Redis队列并分配GPU
 * <p>
 * 调度循环：
 * 1. 从Redis队列出队最高优先级任务
 * 2. 使用BestFit算法查找合适的GPU
 * 3. 分配GPU并转换任务状态 QUEUED→RUNNING
 * 4. 更新GPU状态为BUSY
 */
@Slf4j
@Component
public class TaskDispatcher {

    private final TaskPriorityQueue priorityQueue;
    private final GpuAllocator gpuAllocator;
    private final GpuTaskService taskService;
    private final GpuTaskMapper taskMapper;
    private final GpuMapper gpuMapper;

    public TaskDispatcher(TaskPriorityQueue priorityQueue,
                          GpuAllocator gpuAllocator,
                          GpuTaskService taskService,
                          GpuTaskMapper taskMapper,
                          GpuMapper gpuMapper) {
        this.priorityQueue = priorityQueue;
        this.gpuAllocator = gpuAllocator;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.gpuMapper = gpuMapper;
    }

    /**
     * 调度循环 — 每5秒执行一次
     * 从队列中取出任务并分配GPU
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void dispatch() {
        try {
            // 持续处理队列中的任务，直到队列为空或没有可用GPU
            while (true) {
                Long taskId = priorityQueue.peek();
                if (taskId == null) {
                    // 队列为空，退出循环
                    break;
                }

                // 查询任务详情
                GpuTask task = taskMapper.selectById(taskId);
                if (task == null || task.getStatus() != TaskStatus.QUEUED.getCode()) {
                    // 任务不存在或状态已变更，从队列移除
                    priorityQueue.remove(taskId);
                    log.warn("任务{}不存在或状态已变更，从队列移除", taskId);
                    continue;
                }

                // 使用BestFit算法查找合适的GPU
                Optional<Gpu> gpuOpt = gpuAllocator.allocate(task);
                if (gpuOpt.isEmpty()) {
                    // 没有可用GPU，退出循环等待下次调度
                    log.debug("没有可用GPU满足任务{}的需求，等待下次调度", taskId);
                    break;
                }

                Gpu gpu = gpuOpt.get();

                // 从队列出队
                Long dequeuedTaskId = priorityQueue.dequeue();
                if (dequeuedTaskId == null || !dequeuedTaskId.equals(taskId)) {
                    log.warn("出队任务ID不匹配: 期望{}, 实际{}", taskId, dequeuedTaskId);
                    continue;
                }

                // 分配GPU并转换任务状态 QUEUED→RUNNING
                try {
                    assignGpuToTask(task, gpu);
                    log.info("任务{}已分配到GPU[{}] {}", taskId, gpu.getId(), gpu.getName());
                } catch (Exception e) {
                    log.error("分配GPU失败: taskId={}, gpuId={}", taskId, gpu.getId(), e);
                    // 分配失败，将任务重新入队
                    priorityQueue.enqueue(taskId, task.getBasePriority());
                }
            }
        } catch (Exception e) {
            log.error("调度循环异常", e);
        }
    }

    /**
     * 分配GPU给任务
     * 1. 更新GPU状态为BUSY
     * 2. 计算预估完成时间
     * 3. 转换任务状态 QUEUED→RUNNING
     *
     * @param task 任务
     * @param gpu  分配的GPU
     */
    private void assignGpuToTask(GpuTask task, Gpu gpu) {
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
        LocalDateTime estimatedFinishAt = now.plusSeconds(estimatedSeconds.longValue());
        task.setEstimatedFinishAt(estimatedFinishAt);

        // 3. 转换任务状态 QUEUED→RUNNING，分配GPU
        taskService.transition(task.getId(), TaskStatus.RUNNING, gpu.getId(), null);

        log.info("GPU分配完成: 任务{}分配到GPU[{}], 预估执行{}秒, 预计完成时间{}",
                task.getId(), gpu.getId(), estimatedSeconds, estimatedFinishAt);
    }

    /**
     * 计算预估执行时间
     * 公式: estimatedSeconds = computeUnitsGflop / (computingPowerTflops × 1000)
     *
     * @param computeUnitsGflop      计算量（GFLOP）
     * @param computingPowerTflops   GPU算力（TFLOPS）
     * @return 预估执行秒数
     */
    private BigDecimal calculateEstimatedSeconds(BigDecimal computeUnitsGflop,
                                                  BigDecimal computingPowerTflops) {
        if (computeUnitsGflop == null || computingPowerTflops == null) {
            return BigDecimal.ZERO;
        }

        // TFLOPS转换为GFLOPS: 1 TFLOPS = 1000 GFLOPS
        BigDecimal computingPowerGflops = computingPowerTflops.multiply(new BigDecimal("1000"));

        // estimatedSeconds = computeUnitsGflop / computingPowerGflops
        return computeUnitsGflop.divide(computingPowerGflops, 4, RoundingMode.HALF_UP);
    }

    /**
     * 手动触发一次调度（用于测试）
     */
    public void dispatchOnce() {
        dispatch();
    }
}

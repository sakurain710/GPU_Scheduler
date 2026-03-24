package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.GpuTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 任务执行模拟器 — 使用线程池模拟GPU任务处理
 * <p>
 * 功能：
 * 1. ExecutorService线程池管理异步任务执行
 * 2. Future跟踪任务状态
 * 3. 超时处理（任务执行时间超过预估时间的2倍视为超时）
 * 4. 熔断器模式防止故障传播
 */
@Slf4j
@Service
public class TaskExecutionSimulator {

    private final ExecutorService executorService;
    private final CircuitBreakerService circuitBreaker;
    private final Map<Long, Future<TaskExecutionResult>> runningTasks;

    public TaskExecutionSimulator(CircuitBreakerService circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("gpu-task-executor-" + counter++);
                        thread.setDaemon(false);
                        return thread;
                    }
                }
        );
        this.runningTasks = new ConcurrentHashMap<>();
        log.info("TaskExecutionSimulator initialized with {} threads",
                Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * 提交任务到线程池执行
     *
     * @param task 任务实体
     * @return Future对象，用于跟踪任务状态
     */
    public Future<TaskExecutionResult> submitTask(GpuTask task) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("熔断器开启，拒绝任务{}", task.getId());
            throw new CircuitBreakerOpenException("Circuit breaker is open, rejecting task execution");
        }

        Future<TaskExecutionResult> future = executorService.submit(() -> executeTask(task));
        runningTasks.put(task.getId(), future);
        log.info("任务{}已提交到执行线程池", task.getId());
        return future;
    }

    /**
     * 模拟任务执行
     */
    private TaskExecutionResult executeTask(GpuTask task) {
        long startTime = System.currentTimeMillis();
        log.info("开始执行任务{}: GPU[{}], 预估{}秒",
                task.getId(), task.getGpuId(), task.getEstimatedSeconds());

        try {
            // 模拟GPU计算：实际执行时间 = 预估时间 ± 20%随机波动
            long estimatedMs = task.getEstimatedSeconds().multiply(new BigDecimal("1000")).longValue();
            long actualMs = (long) (estimatedMs * (0.8 + Math.random() * 0.4));

            Thread.sleep(actualMs);

            long elapsed = System.currentTimeMillis() - startTime;
            BigDecimal actualSeconds = new BigDecimal(elapsed).divide(new BigDecimal("1000"), 4, BigDecimal.ROUND_HALF_UP);

            circuitBreaker.recordSuccess();
            log.info("任务{}执行成功: 预估{}秒, 实际{}秒",
                    task.getId(), task.getEstimatedSeconds(), actualSeconds);

            return TaskExecutionResult.success(task.getId(), actualSeconds);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            circuitBreaker.recordFailure();
            log.warn("任务{}被中断", task.getId());
            return TaskExecutionResult.failure(task.getId(), "Task interrupted");

        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.error("任务{}执行失败", task.getId(), e);
            return TaskExecutionResult.failure(task.getId(), e.getMessage());
        }
        // Note: runningTasks entry is NOT removed here.
        // TaskCompletionMonitor retrieves the result and removes it via cancelTask or getResult.
    }

    /**
     * 检查任务是否正在运行
     */
    public boolean isRunning(Long taskId) {
        Future<TaskExecutionResult> future = runningTasks.get(taskId);
        return future != null && !future.isDone();
    }

    /**
     * 获取任务执行结果（非阻塞）
     *
     * @param taskId 任务ID
     * @param timeout 超时时间（秒）
     * @return 执行结果，如果未完成或超时返回null
     */
    public TaskExecutionResult getResult(Long taskId, long timeout) {
        Future<TaskExecutionResult> future = runningTasks.get(taskId);
        if (future == null) {
            return null;
        }

        try {
            TaskExecutionResult result = future.get(timeout, TimeUnit.SECONDS);
            runningTasks.remove(taskId);
            return result;
        } catch (TimeoutException e) {
            log.warn("获取任务{}结果超时", taskId);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待任务{}结果被中断", taskId);
            return null;
        } catch (ExecutionException e) {
            runningTasks.remove(taskId);
            log.error("任务{}执行异常", taskId, e);
            return TaskExecutionResult.failure(taskId, e.getCause().getMessage());
        }
    }

    /**
     * 取消任务执行
     */
    public boolean cancelTask(Long taskId) {
        Future<TaskExecutionResult> future = runningTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            runningTasks.remove(taskId);
            log.info("任务{}取消{}", taskId, cancelled ? "成功" : "失败");
            return cancelled;
        }
        return false;
    }

    /**
     * 获取当前运行中的任务数量
     */
    public int getRunningTaskCount() {
        return runningTasks.size();
    }

    /**
     * 关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭TaskExecutionSimulator...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("TaskExecutionSimulator已关闭");
    }

    /**
     * 任务执行结果
     */
    public static class TaskExecutionResult {
        private final Long taskId;
        private final boolean success;
        private final BigDecimal actualSeconds;
        private final String errorMessage;

        private TaskExecutionResult(Long taskId, boolean success, BigDecimal actualSeconds, String errorMessage) {
            this.taskId = taskId;
            this.success = success;
            this.actualSeconds = actualSeconds;
            this.errorMessage = errorMessage;
        }

        public static TaskExecutionResult success(Long taskId, BigDecimal actualSeconds) {
            return new TaskExecutionResult(taskId, true, actualSeconds, null);
        }

        public static TaskExecutionResult failure(Long taskId, String errorMessage) {
            return new TaskExecutionResult(taskId, false, null, errorMessage);
        }

        public Long getTaskId() {
            return taskId;
        }

        public boolean isSuccess() {
            return success;
        }

        public BigDecimal getActualSeconds() {
            return actualSeconds;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 熔断器开启异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}

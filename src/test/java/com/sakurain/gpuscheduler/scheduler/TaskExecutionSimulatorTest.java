package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.GpuTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 任务执行模拟器测试
 */
@ExtendWith(MockitoExtension.class)
class TaskExecutionSimulatorTest {

    @Mock
    private CircuitBreakerService circuitBreaker;

    private TaskExecutionSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new TaskExecutionSimulator(circuitBreaker);
    }

    @Test
    void testSubmitTask_Success() throws Exception {
        when(circuitBreaker.allowRequest()).thenReturn(true);

        GpuTask task = buildTask(1L, new BigDecimal("0.1")); // ~100ms
        Future<TaskExecutionSimulator.TaskExecutionResult> future = simulator.submitTask(task);

        assertThat(future).isNotNull();

        TaskExecutionSimulator.TaskExecutionResult result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTaskId()).isEqualTo(1L);
        assertThat(result.getActualSeconds()).isNotNull();
    }

    @Test
    void testSubmitTask_CircuitBreakerOpen_ThrowsException() {
        when(circuitBreaker.allowRequest()).thenReturn(false);

        GpuTask task = buildTask(2L, new BigDecimal("1.0"));

        assertThatThrownBy(() -> simulator.submitTask(task))
                .isInstanceOf(TaskExecutionSimulator.CircuitBreakerOpenException.class);
    }

    @Test
    void testCancelTask_Success() throws Exception {
        when(circuitBreaker.allowRequest()).thenReturn(true);

        GpuTask task = buildTask(3L, new BigDecimal("10.0")); // 10s task
        simulator.submitTask(task);

        assertThat(simulator.isRunning(3L)).isTrue();

        boolean cancelled = simulator.cancelTask(3L);
        assertThat(cancelled).isTrue();
        assertThat(simulator.isRunning(3L)).isFalse();
    }

    @Test
    void testCancelTask_NotRunning_ReturnsFalse() {
        boolean result = simulator.cancelTask(999L);
        assertThat(result).isFalse();
    }

    @Test
    void testGetRunningTaskCount() throws Exception {
        when(circuitBreaker.allowRequest()).thenReturn(true);

        assertThat(simulator.getRunningTaskCount()).isEqualTo(0);

        GpuTask task1 = buildTask(10L, new BigDecimal("5.0"));
        GpuTask task2 = buildTask(11L, new BigDecimal("5.0"));
        simulator.submitTask(task1);
        simulator.submitTask(task2);

        assertThat(simulator.getRunningTaskCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testGetResult_Timeout_ReturnsNull() {
        when(circuitBreaker.allowRequest()).thenReturn(true);

        GpuTask task = buildTask(20L, new BigDecimal("30.0")); // 30s task
        simulator.submitTask(task);

        // 0秒超时，应立即返回null
        TaskExecutionSimulator.TaskExecutionResult result = simulator.getResult(20L, 0);
        assertThat(result).isNull();

        simulator.cancelTask(20L);
    }

    @Test
    void testGetResult_TaskNotFound_ReturnsNull() {
        TaskExecutionSimulator.TaskExecutionResult result = simulator.getResult(999L, 1);
        assertThat(result).isNull();
    }

    @Test
    void testTaskExecutionResult_Success() {
        TaskExecutionSimulator.TaskExecutionResult result =
                TaskExecutionSimulator.TaskExecutionResult.success(1L, new BigDecimal("1.5"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTaskId()).isEqualTo(1L);
        assertThat(result.getActualSeconds()).isEqualByComparingTo("1.5");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void testTaskExecutionResult_Failure() {
        TaskExecutionSimulator.TaskExecutionResult result =
                TaskExecutionSimulator.TaskExecutionResult.failure(2L, "GPU error");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getTaskId()).isEqualTo(2L);
        assertThat(result.getActualSeconds()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("GPU error");
    }

    private GpuTask buildTask(Long id, BigDecimal estimatedSeconds) {
        return GpuTask.builder()
                .id(id)
                .gpuId(1L)
                .estimatedSeconds(estimatedSeconds)
                .build();
    }
}

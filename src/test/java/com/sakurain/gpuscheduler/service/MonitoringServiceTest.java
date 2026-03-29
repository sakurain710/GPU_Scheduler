package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.dto.monitor.GpuMetrics;
import com.sakurain.gpuscheduler.dto.monitor.SystemHealth;
import com.sakurain.gpuscheduler.dto.monitor.TaskMetrics;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.CircuitBreakerService;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock private GpuTaskMapper gpuTaskMapper;
    @Mock private GpuMapper gpuMapper;
    @Mock private TaskPriorityQueue priorityQueue;
    @Mock private CircuitBreakerService circuitBreaker;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private RedisConnectionFactory connectionFactory;
    @Mock private RedisConnection redisConnection;
    @Mock private TaskRetryDlqService retryDlqService;
    @Mock private TaskNotificationService taskNotificationService;

    @InjectMocks
    private MonitoringService monitoringService;

    private Gpu idleGpu;
    private Gpu busyGpu;
    private GpuTask completedTask;
    private GpuTask failedTask;
    private GpuTask queuedTask;
    private GpuTask runningTask;

    @BeforeEach
    void setUp() {
        idleGpu = Gpu.builder().id(1L).memoryGb(new BigDecimal("40")).status(GpuStatus.IDLE.getCode()).build();
        busyGpu = Gpu.builder().id(2L).memoryGb(new BigDecimal("80")).status(GpuStatus.BUSY.getCode()).build();
        lenient().when(retryDlqService.retryQueueSize()).thenReturn(0L);
        lenient().when(retryDlqService.dlqSize()).thenReturn(0L);
        lenient().when(taskNotificationService.webhookRetryQueueSize()).thenReturn(0L);

        completedTask = GpuTask.builder()
                .id(10L).status(TaskStatus.COMPLETED.getCode())
                .basePriority(8).build();

        failedTask = GpuTask.builder()
                .id(11L).status(TaskStatus.FAILED.getCode())
                .basePriority(3).errorMessage("Out of memory error").build();

        queuedTask = GpuTask.builder()
                .id(12L).status(TaskStatus.QUEUED.getCode())
                .basePriority(5).enqueueAt(LocalDateTime.now().minusSeconds(30)).build();

        runningTask = GpuTask.builder()
                .id(13L).status(TaskStatus.RUNNING.getCode())
                .gpuId(2L).minMemoryGb(new BigDecimal("20")).build();
    }

    // ── getTaskMetrics ────────────────────────────────────────────────────────

    @Test
    void getTaskMetrics_countsStatusesCorrectly() {
        when(gpuTaskMapper.selectList(null)).thenReturn(List.of(completedTask, failedTask, queuedTask));
        when(priorityQueue.size()).thenReturn(1L);

        TaskMetrics metrics = monitoringService.getTaskMetrics();

        assertThat(metrics.getQueueLength()).isEqualTo(1L);
        assertThat(metrics.getTaskCountByStatus().get("Completed")).isEqualTo(1L);
        assertThat(metrics.getTaskCountByStatus().get("Failed")).isEqualTo(1L);
        assertThat(metrics.getTaskCountByStatus().get("Queued")).isEqualTo(1L);
        assertThat(metrics.getTaskCountByStatus().get("Running")).isEqualTo(0L);
    }

    @Test
    void getTaskMetrics_completionRate_calculatedFromTerminalTasks() {
        when(gpuTaskMapper.selectList(null)).thenReturn(List.of(completedTask, failedTask));
        when(priorityQueue.size()).thenReturn(0L);

        TaskMetrics metrics = monitoringService.getTaskMetrics();

        // 1 completed / 2 terminal = 50%
        assertThat(metrics.getCompletionRate()).isEqualTo("50.0%");
        assertThat(metrics.getFailureRate()).isEqualTo("50.0%");
    }

    @Test
    void getTaskMetrics_noTerminalTasks_zeroRates() {
        when(gpuTaskMapper.selectList(null)).thenReturn(List.of(queuedTask));
        when(priorityQueue.size()).thenReturn(1L);

        TaskMetrics metrics = monitoringService.getTaskMetrics();

        assertThat(metrics.getCompletionRate()).isEqualTo("0.0%");
        assertThat(metrics.getFailureRate()).isEqualTo("0.0%");
    }

    @Test
    void getTaskMetrics_failureReasons_groupedByMessage() {
        GpuTask f2 = GpuTask.builder().id(20L).status(TaskStatus.FAILED.getCode())
                .errorMessage("Out of memory error").build();
        when(gpuTaskMapper.selectList(null)).thenReturn(List.of(failedTask, f2));
        when(priorityQueue.size()).thenReturn(0L);

        TaskMetrics metrics = monitoringService.getTaskMetrics();

        assertThat(metrics.getFailureReasons().get("Out of memory error")).isEqualTo(2L);
    }

    @Test
    void getTaskMetrics_avgWaitTime_onlyForQueuedTasks() {
        when(gpuTaskMapper.selectList(null)).thenReturn(List.of(queuedTask, completedTask));
        when(priorityQueue.size()).thenReturn(1L);

        TaskMetrics metrics = monitoringService.getTaskMetrics();

        // queuedTask has basePriority=5 → "Medium(5-7)"
        assertThat(metrics.getAvgWaitSecondsByPriority()).containsKey("Medium(5-7)");
        assertThat(metrics.getAvgWaitSecondsByPriority().get("Medium(5-7)")).isGreaterThanOrEqualTo(29.0);
        assertThat(metrics.getDispatchLatencyPercentilesSeconds()).containsKeys("p50", "p95", "p99");
        assertThat(metrics.getQueueAgeHistogram()).containsKeys("lt_1m", "m1_5", "m5_15", "gte_15m");
    }

    @Test
    void getTaskMetrics_userSlaCompliance_andAllocationFailureReasons() {
        GpuTask completedOnTime = GpuTask.builder()
                .id(21L).userId(7L).status(TaskStatus.COMPLETED.getCode())
                .estimatedSeconds(new BigDecimal("100"))
                .actualSeconds(new BigDecimal("90"))
                .build();
        GpuTask completedLate = GpuTask.builder()
                .id(22L).userId(7L).status(TaskStatus.COMPLETED.getCode())
                .estimatedSeconds(new BigDecimal("100"))
                .actualSeconds(new BigDecimal("130"))
                .build();
        GpuTask allocationFailed = GpuTask.builder()
                .id(23L).status(TaskStatus.FAILED.getCode())
                .errorMessage("No available GPU to allocate")
                .build();
        when(gpuTaskMapper.selectList(null)).thenReturn(List.of(completedOnTime, completedLate, allocationFailed));
        when(priorityQueue.size()).thenReturn(0L);

        TaskMetrics metrics = monitoringService.getTaskMetrics();
        assertThat(metrics.getUserSlaCompliancePct()).containsEntry(7L, 50.0);
        assertThat(metrics.getAllocationFailureReasons()).containsEntry("NO_AVAILABLE_GPU", 1L);
    }

    // ── getGpuMetrics ─────────────────────────────────────────────────────────

    @Test
    void getGpuMetrics_utilizationRate_calculatedCorrectly() {
        when(gpuMapper.selectList(null)).thenReturn(List.of(idleGpu, busyGpu));
        when(gpuTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(gpuTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        GpuMetrics metrics = monitoringService.getGpuMetrics();

        assertThat(metrics.getTotal()).isEqualTo(2L);
        assertThat(metrics.getUtilizationRate()).isEqualTo("50.0%");
        assertThat(metrics.getCountByStatus().get("Idle")).isEqualTo(1L);
        assertThat(metrics.getCountByStatus().get("Busy")).isEqualTo(1L);
    }

    @Test
    void getGpuMetrics_vramFragmentation_calculatedForBusyGpu() {
        when(gpuMapper.selectList(null)).thenReturn(List.of(busyGpu));
        // runningTask: minMemoryGb=20, busyGpu.memoryGb=80 → fragmentation = 1 - 20/80 = 0.75
        when(gpuTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));

        GpuMetrics metrics = monitoringService.getGpuMetrics();

        assertThat(metrics.getVramFragmentationByGpu()).containsKey(2L);
        assertThat(metrics.getVramFragmentationByGpu().get(2L))
                .isEqualByComparingTo(new BigDecimal("0.7500"));
    }

    @Test
    void getGpuMetrics_idleSeconds_calculatedForIdleGpu() {
        GpuTask lastTask = GpuTask.builder().id(5L).gpuId(1L)
                .finishedAt(LocalDateTime.now().minusSeconds(120)).build();
        when(gpuMapper.selectList(null)).thenReturn(List.of(idleGpu));
        when(gpuTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(gpuTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(lastTask);

        GpuMetrics metrics = monitoringService.getGpuMetrics();

        assertThat(metrics.getIdleSecondsByGpu()).containsKey(1L);
        assertThat(metrics.getIdleSecondsByGpu().get(1L)).isGreaterThanOrEqualTo(119L);
    }

    @Test
    void getGpuMetrics_noGpus_zeroRate() {
        when(gpuMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(gpuTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        GpuMetrics metrics = monitoringService.getGpuMetrics();

        assertThat(metrics.getTotal()).isEqualTo(0L);
        assertThat(metrics.getUtilizationRate()).isEqualTo("0.0%");
    }

    // ── getSystemHealth ───────────────────────────────────────────────────────

    @Test
    void getSystemHealth_allUp_returnsUp() {
        when(gpuMapper.selectCount(null)).thenReturn(2L);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(circuitBreaker.getState()).thenReturn(CircuitBreakerService.State.CLOSED);
        when(priorityQueue.allMembers()).thenReturn(Collections.emptySet());

        SystemHealth health = monitoringService.getSystemHealth();

        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getDbStatus()).isEqualTo("UP");
        assertThat(health.getRedisStatus()).isEqualTo("UP");
        assertThat(health.getCircuitBreakerState()).isEqualTo("CLOSED");
        assertThat(health.getOldestQueuedTaskSeconds()).isEqualTo(-1L);
    }

    @Test
    void getSystemHealth_circuitBreakerOpen_returnsDegraded() {
        when(gpuMapper.selectCount(null)).thenReturn(1L);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(circuitBreaker.getState()).thenReturn(CircuitBreakerService.State.OPEN);
        when(priorityQueue.allMembers()).thenReturn(Collections.emptySet());

        SystemHealth health = monitoringService.getSystemHealth();

        assertThat(health.getStatus()).isEqualTo("DEGRADED");
        assertThat(health.getCircuitBreakerState()).isEqualTo("OPEN");
    }

    @Test
    void getSystemHealth_dbDown_returnsDown() {
        when(gpuMapper.selectCount(null)).thenThrow(new RuntimeException("Connection refused"));
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(circuitBreaker.getState()).thenReturn(CircuitBreakerService.State.CLOSED);
        when(priorityQueue.allMembers()).thenReturn(Collections.emptySet());

        SystemHealth health = monitoringService.getSystemHealth();

        assertThat(health.getStatus()).isEqualTo("DOWN");
        assertThat(health.getDbStatus()).isEqualTo("DOWN");
    }

    @Test
    void getSystemHealth_withQueuedTask_returnsOldestWaitTime() {
        GpuTask oldTask = GpuTask.builder().id(99L)
                .enqueueAt(LocalDateTime.now().minusSeconds(300)).build();
        when(gpuMapper.selectCount(null)).thenReturn(1L);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(circuitBreaker.getState()).thenReturn(CircuitBreakerService.State.CLOSED);
        when(priorityQueue.allMembers()).thenReturn(Set.of("99"));
        when(gpuTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(oldTask);

        SystemHealth health = monitoringService.getSystemHealth();

        assertThat(health.getOldestQueuedTaskSeconds()).isGreaterThanOrEqualTo(299L);
    }
}

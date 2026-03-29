package com.sakurain.gpuscheduler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.TaskRetryDlqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * 任务完成监控器单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskCompletionMonitorTest {

    @Mock
    private GpuTaskMapper taskMapper;

    @Mock
    private GpuMapper gpuMapper;

    @Mock
    private GpuTaskService taskService;

    @Mock
    private TaskExecutionSimulator simulator;

    @Mock
    private TaskRetryDlqService retryDlqService;

    @InjectMocks
    private TaskCompletionMonitor monitor;

    private GpuTask runningTask;

    @BeforeEach
    void setUp() {
        runningTask = GpuTask.builder()
                .id(100L)
                .gpuId(1L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("5.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(3))
                .build();
    }

    @Test
    void testMonitor_NoRunningTasks_DoesNothing() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        monitor.monitorRunningTasks();

        verify(taskService, never()).transition(anyLong(), any(), any(), any());
    }

    @Test
    void testMonitor_TaskCompleted_TransitionsToCompleted() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.success(100L, new BigDecimal("4.8"))
        );
        when(gpuMapper.tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(1);

        monitor.monitorRunningTasks();

        verify(taskService).transition(100L, TaskStatus.COMPLETED, 1L, null);
        verify(gpuMapper).tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode());
        verify(retryDlqService, never()).onTaskFailed(anyLong(), any());
    }

    @Test
    void testMonitor_TaskFailed_TransitionsToFailed() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.failure(100L, "GPU error")
        );
        when(gpuMapper.tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(1);

        monitor.monitorRunningTasks();

        verify(taskService).transition(100L, TaskStatus.FAILED, 1L, null);
        verify(gpuMapper).tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode());
        verify(retryDlqService).onTaskFailed(100L, "GPU error");
    }

    @Test
    void testMonitor_TaskTimedOut_ForceFails() {
        GpuTask timedOutTask = GpuTask.builder()
                .id(200L)
                .gpuId(2L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("1.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(10))
                .build();

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(timedOutTask));
        when(gpuMapper.tryMarkIdle(2L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(1);

        monitor.monitorRunningTasks();

        verify(simulator).cancelTask(200L);
        verify(taskService).transition(200L, TaskStatus.FAILED, 2L, null);
        verify(gpuMapper).tryMarkIdle(2L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode());
        verify(retryDlqService).onTaskFailed(200L, "Task execution timed out");
    }

    @Test
    void testMonitor_TaskStillRunning_DoesNotComplete() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(true);

        monitor.monitorRunningTasks();

        verify(taskService, never()).transition(anyLong(), any(), any(), any());
    }

    @Test
    void testMonitor_GpuRelease_OnlyIfBusy() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.success(100L, new BigDecimal("4.0"))
        );
        when(gpuMapper.tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(0);

        monitor.monitorRunningTasks();

        verify(gpuMapper).tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode());
    }

    @Test
    void testMonitor_ActualSecondsRecorded() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.success(100L, new BigDecimal("4.8"))
        );
        when(gpuMapper.tryMarkIdle(1L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(1);

        monitor.monitorRunningTasks();

        ArgumentCaptor<GpuTask> captor = ArgumentCaptor.forClass(GpuTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getActualSeconds()).isEqualByComparingTo("4.8");
    }

    @Test
    void testMonitor_OrphanRunningTaskRecoveredAfterThreshold() {
        GpuTask orphanTask = GpuTask.builder()
                .id(300L)
                .gpuId(3L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("120.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(130))
                .build();
        ReflectionTestUtils.setField(monitor, "orphanRunningThresholdSeconds", 120L);

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orphanTask));
        when(simulator.isRunning(300L)).thenReturn(false);
        when(simulator.getResult(eq(300L), eq(1L))).thenReturn(null);
        when(gpuMapper.tryMarkIdle(3L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(1);

        monitor.monitorRunningTasks();

        verify(taskService).transition(300L, TaskStatus.FAILED, 3L, null);
        verify(retryDlqService).onTaskFailed(300L, "Orphan RUNNING task recovered after restart");
    }

    @Test
    void testMonitor_OrphanBelowThreshold_NotRecovered() {
        GpuTask almostOrphan = GpuTask.builder()
                .id(301L)
                .gpuId(4L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("120.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(119))
                .build();
        ReflectionTestUtils.setField(monitor, "orphanRunningThresholdSeconds", 120L);

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(almostOrphan));
        when(simulator.isRunning(301L)).thenReturn(false);
        when(simulator.getResult(eq(301L), eq(1L))).thenReturn(null);

        monitor.monitorRunningTasks();

        verify(taskService, never()).transition(eq(301L), any(), any(), any());
        verify(retryDlqService, never()).onTaskFailed(eq(301L), any());
    }

    @Test
    void testMonitor_DbPartialFailure_ContinuesOtherTasks() {
        GpuTask broken = GpuTask.builder()
                .id(401L)
                .gpuId(5L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("1.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(10))
                .build();
        GpuTask healthy = GpuTask.builder()
                .id(402L)
                .gpuId(6L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("1.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(10))
                .build();

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(broken, healthy));
        when(simulator.cancelTask(anyLong())).thenReturn(true);
        doThrow(new RuntimeException("db fail"))
                .when(taskMapper).updateById((GpuTask) argThat(t -> t != null && Long.valueOf(401L).equals(((GpuTask) t).getId())));
        when(gpuMapper.tryMarkIdle(6L, GpuStatus.BUSY.getCode(), GpuStatus.IDLE.getCode())).thenReturn(1);

        monitor.monitorRunningTasks();

        verify(taskService, never()).transition(401L, TaskStatus.FAILED, 5L, null);
        verify(taskService).transition(402L, TaskStatus.FAILED, 6L, null);
    }
}

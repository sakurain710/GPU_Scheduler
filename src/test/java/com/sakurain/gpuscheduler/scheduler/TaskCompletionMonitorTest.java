package com.sakurain.gpuscheduler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

/**
 * 任务完成监控器测试
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

    @InjectMocks
    private TaskCompletionMonitor monitor;

    private GpuTask runningTask;
    private Gpu busyGpu;

    @BeforeEach
    void setUp() {
        runningTask = GpuTask.builder()
                .id(100L)
                .gpuId(1L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("5.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(3))
                .build();

        busyGpu = Gpu.builder()
                .id(1L)
                .name("NVIDIA A100")
                .status(GpuStatus.BUSY.getCode())
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
        when(gpuMapper.selectById(1L)).thenReturn(busyGpu);

        monitor.monitorRunningTasks();

        verify(taskService).transition(100L, TaskStatus.COMPLETED, 1L, null);
        verify(gpuMapper).updateById(ArgumentMatchers.<Gpu>argThat(g -> g.getStatus() == GpuStatus.IDLE.getCode()));
    }

    @Test
    void testMonitor_TaskFailed_TransitionsToFailed() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.failure(100L, "GPU error")
        );
        when(gpuMapper.selectById(1L)).thenReturn(busyGpu);

        monitor.monitorRunningTasks();

        verify(taskService).transition(100L, TaskStatus.FAILED, 1L, null);
        verify(gpuMapper).updateById(ArgumentMatchers.<Gpu>argThat(g -> g.getStatus() == GpuStatus.IDLE.getCode()));
    }

    @Test
    void testMonitor_TaskTimedOut_ForceFails() {
        // 任务已超过预估时间2倍
        GpuTask timedOutTask = GpuTask.builder()
                .id(200L)
                .gpuId(2L)
                .status(TaskStatus.RUNNING.getCode())
                .estimatedSeconds(new BigDecimal("1.0"))
                .dispatchedAt(LocalDateTime.now().minusSeconds(10)) // 10s ago, timeout = 2s
                .build();

        Gpu gpu2 = Gpu.builder().id(2L).status(GpuStatus.BUSY.getCode()).build();

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(timedOutTask));
        when(gpuMapper.selectById(2L)).thenReturn(gpu2);

        monitor.monitorRunningTasks();

        verify(simulator).cancelTask(200L);
        verify(taskService).transition(200L, TaskStatus.FAILED, 2L, null);
        verify(gpuMapper).updateById(ArgumentMatchers.<Gpu>argThat(g -> g.getStatus() == GpuStatus.IDLE.getCode()));
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
        Gpu idleGpu = Gpu.builder().id(1L).status(GpuStatus.IDLE.getCode()).build();

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.success(100L, new BigDecimal("4.0"))
        );
        when(gpuMapper.selectById(1L)).thenReturn(idleGpu);

        monitor.monitorRunningTasks();

        // GPU已经是IDLE，不应再次更新
        verify(gpuMapper, never()).updateById(ArgumentMatchers.<Gpu>any());
    }

    @Test
    void testMonitor_ActualSecondsRecorded() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningTask));
        when(simulator.isRunning(100L)).thenReturn(false);
        when(simulator.getResult(eq(100L), eq(1L))).thenReturn(
                TaskExecutionSimulator.TaskExecutionResult.success(100L, new BigDecimal("4.8"))
        );
        when(gpuMapper.selectById(1L)).thenReturn(busyGpu);

        monitor.monitorRunningTasks();

        ArgumentCaptor<GpuTask> captor = ArgumentCaptor.forClass(GpuTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getActualSeconds()).isEqualByComparingTo("4.8");
    }
}

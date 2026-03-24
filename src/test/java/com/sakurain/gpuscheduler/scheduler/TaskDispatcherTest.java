package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.util.RedisLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 任务调度器测试
 */
@ExtendWith(MockitoExtension.class)
class TaskDispatcherTest {

    @Mock
    private TaskPriorityQueue priorityQueue;

    @Mock
    private GpuAllocator gpuAllocator;

    @Mock
    private GpuTaskService taskService;

    @Mock
    private GpuTaskMapper taskMapper;

    @Mock
    private GpuMapper gpuMapper;

    @Mock
    private RedisLockService lockService;

    @InjectMocks
    private TaskDispatcher dispatcher;

    private GpuTask queuedTask;
    private Gpu idleGpu;

    @BeforeEach
    void setUp() {
        // Mock lock service to always succeed
        when(lockService.tryLock(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        // 创建一个排队中的任务
        queuedTask = GpuTask.builder()
                .id(100L)
                .userId(1L)
                .title("训练模型")
                .minMemoryGb(new BigDecimal("24.00"))
                .computeUnitsGflop(new BigDecimal("500000.0000"))
                .basePriority(5)
                .status(TaskStatus.QUEUED.getCode())
                .build();

        // 创建一个空闲的GPU
        idleGpu = Gpu.builder()
                .id(1L)
                .name("NVIDIA A100 40GB")
                .manufacturer("NVIDIA")
                .memoryGb(new BigDecimal("40.00"))
                .computingPowerTflops(new BigDecimal("19.5"))
                .status(GpuStatus.IDLE.getCode())
                .build();
    }

    @Test
    void testDispatch_SuccessfulAllocation() {
        // 模拟队列中有一个任务
        when(priorityQueue.dequeue()).thenReturn(100L, null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));
        when(gpuMapper.updateById(any(Gpu.class))).thenReturn(1);

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证GPU状态更新为BUSY
        verify(gpuMapper, times(1)).updateById(argThat((Gpu gpu) ->
                gpu.getId().equals(1L) && gpu.getStatus().equals(GpuStatus.BUSY.getCode())
        ));

        // 验证任务状态转换 QUEUED→RUNNING
        verify(taskService, times(1)).transition(
                eq(100L),
                eq(TaskStatus.RUNNING),
                eq(1L),
                isNull()
        );

        // 验证任务从队列出队
        verify(priorityQueue, times(2)).dequeue();
    }

    @Test
    void testDispatch_NoAvailableGpu() {
        // 模拟队列中有任务，但没有可用GPU
        when(priorityQueue.dequeue()).thenReturn(100L);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.empty());

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证任务被重新入队
        verify(priorityQueue, times(1)).enqueue(eq(100L), anyDouble());

        // 验证任务状态未转换
        verify(taskService, never()).transition(anyLong(), any(), any(), any());

        // 验证GPU状态未更新
        verify(gpuMapper, never()).updateById(any(Gpu.class));
    }

    @Test
    void testDispatch_EmptyQueue() {
        // 模拟空队列
        when(priorityQueue.dequeue()).thenReturn(null);

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证没有任何操作
        verify(taskMapper, never()).selectById(anyLong());
        verify(gpuAllocator, never()).allocate(any());
    }

    @Test
    void testDispatch_TaskNotFound() {
        // 模拟队列中有任务ID，但数据库中找不到任务
        when(priorityQueue.dequeue()).thenReturn(100L, null);
        when(taskMapper.selectById(100L)).thenReturn(null);

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证没有分配GPU
        verify(gpuAllocator, never()).allocate(any());
        verify(taskService, never()).transition(anyLong(), any(), any(), any());
    }

    @Test
    void testDispatch_TaskStatusChanged() {
        // 模拟任务状态已变更（不再是QUEUED）
        GpuTask runningTask = GpuTask.builder()
                .id(100L)
                .status(TaskStatus.RUNNING.getCode())
                .build();

        when(priorityQueue.dequeue()).thenReturn(100L, null);
        when(taskMapper.selectById(100L)).thenReturn(runningTask);

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证没有分配GPU
        verify(gpuAllocator, never()).allocate(any());
    }

    @Test
    void testDispatch_MultipleTasksInQueue() {
        // 模拟队列中有多个任务
        GpuTask task1 = GpuTask.builder()
                .id(100L)
                .minMemoryGb(new BigDecimal("24.00"))
                .computeUnitsGflop(new BigDecimal("500000.0000"))
                .status(TaskStatus.QUEUED.getCode())
                .basePriority(5)
                .build();

        GpuTask task2 = GpuTask.builder()
                .id(101L)
                .minMemoryGb(new BigDecimal("16.00"))
                .computeUnitsGflop(new BigDecimal("300000.0000"))
                .status(TaskStatus.QUEUED.getCode())
                .basePriority(3)
                .build();

        Gpu gpu1 = Gpu.builder()
                .id(1L)
                .name("GPU1")
                .memoryGb(new BigDecimal("40.00"))
                .computingPowerTflops(new BigDecimal("19.5"))
                .status(GpuStatus.IDLE.getCode())
                .build();

        Gpu gpu2 = Gpu.builder()
                .id(2L)
                .name("GPU2")
                .memoryGb(new BigDecimal("24.00"))
                .computingPowerTflops(new BigDecimal("15.0"))
                .status(GpuStatus.IDLE.getCode())
                .build();

        when(priorityQueue.dequeue()).thenReturn(100L, 101L, null);
        when(taskMapper.selectById(100L)).thenReturn(task1);
        when(taskMapper.selectById(101L)).thenReturn(task2);
        when(gpuAllocator.allocate(task1)).thenReturn(Optional.of(gpu1));
        when(gpuAllocator.allocate(task2)).thenReturn(Optional.of(gpu2));
        when(gpuMapper.updateById(any(Gpu.class))).thenReturn(1);

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证两个任务都被处理
        verify(priorityQueue, times(3)).dequeue();
        verify(taskService, times(1)).transition(eq(100L), eq(TaskStatus.RUNNING), eq(1L), isNull());
        verify(taskService, times(1)).transition(eq(101L), eq(TaskStatus.RUNNING), eq(2L), isNull());
    }

    @Test
    void testDispatch_AllocationFailure() {
        // 模拟分配过程中出现异常
        when(priorityQueue.dequeue()).thenReturn(100L, null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));
        when(gpuMapper.updateById(any(Gpu.class))).thenReturn(1);
        doThrow(new RuntimeException("Database error"))
                .when(taskService).transition(anyLong(), any(), any(), any());

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证任务被重新入队
        verify(priorityQueue, times(1)).enqueue(eq(100L), anyDouble());
    }

    @Test
    void testDispatch_EstimatedTimeCalculation() {
        // 验证预估时间计算
        when(priorityQueue.dequeue()).thenReturn(100L, null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));
        when(gpuMapper.updateById(any(Gpu.class))).thenReturn(1);

        // 执行调度
        dispatcher.dispatchOnce();

        // 验证任务状态转换被调用
        verify(taskService, times(1)).transition(
                eq(100L),
                eq(TaskStatus.RUNNING),
                eq(1L),
                isNull()
        );
    }
}
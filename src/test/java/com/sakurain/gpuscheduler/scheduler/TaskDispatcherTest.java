package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
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
    private GpuTaskMapper taskMapper;

    @Mock
    private RedisLockService lockService;

    @Mock
    private TaskAssignmentService assignmentService;

    @InjectMocks
    private TaskDispatcher dispatcher;

    private GpuTask queuedTask;
    private Gpu idleGpu;

    @BeforeEach
    void setUp() {
        when(lockService.tryLock(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        queuedTask = GpuTask.builder()
                .id(100L)
                .userId(1L)
                .title("训练模型")
                .minMemoryGb(new BigDecimal("24.00"))
                .computeUnitsGflop(new BigDecimal("500000.0000"))
                .basePriority(5)
                .status(TaskStatus.QUEUED.getCode())
                .build();

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
        when(priorityQueue.dequeue()).thenReturn(100L, (Long) null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));

        dispatcher.dispatchOnce();

        verify(assignmentService, times(1)).assign(queuedTask, idleGpu);
        verify(priorityQueue, times(2)).dequeue();
    }

    @Test
    void testDispatch_NoAvailableGpu() {
        when(priorityQueue.dequeue()).thenReturn(100L);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.empty());

        dispatcher.dispatchOnce();

        verify(priorityQueue, times(1)).enqueue(eq(100L), anyDouble());
        verify(assignmentService, never()).assign(any(), any());
    }

    @Test
    void testDispatch_EmptyQueue() {
        when(priorityQueue.dequeue()).thenReturn(null);

        dispatcher.dispatchOnce();

        verify(taskMapper, never()).selectById(anyLong());
        verify(gpuAllocator, never()).allocate(any());
    }

    @Test
    void testDispatch_TaskNotFound() {
        when(priorityQueue.dequeue()).thenReturn(100L, (Long) null);
        when(taskMapper.selectById(100L)).thenReturn(null);

        dispatcher.dispatchOnce();

        verify(gpuAllocator, never()).allocate(any());
        verify(assignmentService, never()).assign(any(), any());
    }

    @Test
    void testDispatch_TaskStatusChanged() {
        GpuTask runningTask = GpuTask.builder()
                .id(100L)
                .status(TaskStatus.RUNNING.getCode())
                .build();

        when(priorityQueue.dequeue()).thenReturn(100L, (Long) null);
        when(taskMapper.selectById(100L)).thenReturn(runningTask);

        dispatcher.dispatchOnce();

        verify(gpuAllocator, never()).allocate(any());
    }

    @Test
    void testDispatch_MultipleTasksInQueue() {
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

        Gpu gpu1 = Gpu.builder().id(1L).name("GPU1")
                .memoryGb(new BigDecimal("40.00"))
                .computingPowerTflops(new BigDecimal("19.5"))
                .status(GpuStatus.IDLE.getCode()).build();

        Gpu gpu2 = Gpu.builder().id(2L).name("GPU2")
                .memoryGb(new BigDecimal("24.00"))
                .computingPowerTflops(new BigDecimal("15.0"))
                .status(GpuStatus.IDLE.getCode()).build();

        when(priorityQueue.dequeue()).thenReturn(100L, 101L, null);
        when(taskMapper.selectById(100L)).thenReturn(task1);
        when(taskMapper.selectById(101L)).thenReturn(task2);
        when(gpuAllocator.allocate(task1)).thenReturn(Optional.of(gpu1));
        when(gpuAllocator.allocate(task2)).thenReturn(Optional.of(gpu2));

        dispatcher.dispatchOnce();

        verify(priorityQueue, times(3)).dequeue();
        verify(assignmentService, times(1)).assign(task1, gpu1);
        verify(assignmentService, times(1)).assign(task2, gpu2);
    }


    @Test
    void testDispatch_AllocationFailure_BreaksCurrentLoopToAvoidHotRetry() {
        when(priorityQueue.dequeue()).thenReturn(100L, 100L, null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));
        doThrow(new RuntimeException("Database error"))
                .when(assignmentService).assign(any(), any());

        dispatcher.dispatchOnce();

        verify(priorityQueue, times(1)).dequeue();
        verify(priorityQueue, times(1)).enqueue(eq(100L), anyDouble());
    }
    @Test
    void testDispatch_AllocationFailure() {
        when(priorityQueue.dequeue()).thenReturn(100L, (Long) null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));
        doThrow(new RuntimeException("Database error"))
                .when(assignmentService).assign(any(), any());

        dispatcher.dispatchOnce();

        verify(priorityQueue, times(1)).enqueue(eq(100L), anyDouble());
    }

    @Test
    void testDispatch_EstimatedTimeCalculation() {
        when(priorityQueue.dequeue()).thenReturn(100L, (Long) null);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.of(idleGpu));

        dispatcher.dispatchOnce();

        verify(assignmentService, times(1)).assign(queuedTask, idleGpu);
    }

    @Test
    void testDispatch_ExponentialBackoff_TriggersAfterMaxFailures() {
        // 连续5次无可用GPU，触发熔断
        when(priorityQueue.dequeue()).thenReturn(100L);
        when(taskMapper.selectById(100L)).thenReturn(queuedTask);
        when(gpuAllocator.allocate(queuedTask)).thenReturn(Optional.empty());

        for (int i = 0; i < MAX_CONSECUTIVE_FAILURES; i++) {
            dispatcher.dispatchOnce();
        }

        // 第6次调度应被退避跳过（不获取锁，不出队）
        dispatcher.dispatchOnce();
        // dequeue 只被调用了 MAX_CONSECUTIVE_FAILURES 次，第6次被退避拦截
        verify(priorityQueue, times(MAX_CONSECUTIVE_FAILURES)).dequeue();
    }

    private static final int MAX_CONSECUTIVE_FAILURES = 5;
}

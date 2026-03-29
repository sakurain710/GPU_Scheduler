package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.config.SchedulerConfig;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务老化调度器测试
 */
@ExtendWith(MockitoExtension.class)
class TaskAgingSchedulerTest {

    @Mock
    private TaskPriorityQueue priorityQueue;

    @Mock
    private GpuTaskMapper taskMapper;

    @Mock
    private SchedulerConfig config;

    @InjectMocks
    private TaskAgingScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(config.getAgeWeightPerMinute()).thenReturn(0.1);
        lenient().when(config.isAgingEnabled()).thenReturn(true);
        lenient().when(config.isScheduledJobsEnabled()).thenReturn(true);
    }

    @Test
    void testCalculateEffectivePriority_NoWaitTime() {
        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(LocalDateTime.now())
                .build();

        double priority = scheduler.calculateEffectivePriority(task);
        assertEquals(5.0, priority, 0.1);
    }

    @Test
    void testCalculateEffectivePriority_WithWaitTime() {
        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(LocalDateTime.now().minusMinutes(10))
                .build();

        double priority = scheduler.calculateEffectivePriority(task);
        assertEquals(6.0, priority, 0.1);
    }

    @Test
    void testCalculateEffectivePriority_LongWaitTime() {
        GpuTask task = GpuTask.builder()
                .basePriority(3)
                .enqueueAt(LocalDateTime.now().minusMinutes(100))
                .build();

        double priority = scheduler.calculateEffectivePriority(task);
        assertEquals(13.0, priority, 0.1);
    }

    @Test
    void testCalculateEffectivePriority_NoEnqueueTime() {
        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(null)
                .build();

        double priority = scheduler.calculateEffectivePriority(task);
        assertEquals(5.0, priority, 0.01);
    }

    @Test
    void testCalculateEffectivePriority_CustomAgeWeight() {
        when(config.getAgeWeightPerMinute()).thenReturn(0.5);

        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(LocalDateTime.now().minusMinutes(10))
                .build();

        double priority = scheduler.calculateEffectivePriority(task);
        assertEquals(10.0, priority, 0.1);
    }

    @Test
    void testRefreshTaskPriorities_Success() {
        scheduler.refreshTaskPriorities();
        verify(priorityQueue, times(1)).refreshScores(any());
    }

    @Test
    void testRefreshTaskPriorities_AgingDisabled() {
        when(config.isAgingEnabled()).thenReturn(false);

        scheduler.refreshTaskPriorities();

        verify(priorityQueue, never()).refreshScores(any());
    }

    @Test
    void testCalculateEffectivePriority_PreventStarvation() {
        GpuTask lowPriorityOldTask = GpuTask.builder()
                .basePriority(1)
                .enqueueAt(LocalDateTime.now().minusMinutes(100))
                .build();

        GpuTask highPriorityNewTask = GpuTask.builder()
                .basePriority(10)
                .enqueueAt(LocalDateTime.now())
                .build();

        double lowPriority = scheduler.calculateEffectivePriority(lowPriorityOldTask);
        double highPriority = scheduler.calculateEffectivePriority(highPriorityNewTask);

        assertTrue(lowPriority > highPriority,
                "低优先级老任务应超过高优先级新任务，防止饥饿");
    }
}

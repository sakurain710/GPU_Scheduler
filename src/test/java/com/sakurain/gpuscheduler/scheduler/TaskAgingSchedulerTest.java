package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.config.SchedulerConfig;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

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
        // 默认配置 (lenient to avoid UnnecessaryStubbingException)
        lenient().when(config.getAgeWeightPerMinute()).thenReturn(0.1);
        lenient().when(config.isAgingEnabled()).thenReturn(true);
    }

    @Test
    void testCalculateEffectivePriority_NoWaitTime() {
        // 任务刚入队，等待时间为0
        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(LocalDateTime.now())
                .build();

        double priority = scheduler.calculateEffectivePriority(task);

        // 等待时间接近0，有效优先级应该等于基础优先级
        assertEquals(5.0, priority, 0.1);
    }

    @Test
    void testCalculateEffectivePriority_WithWaitTime() {
        // 任务等待了10分钟
        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(LocalDateTime.now().minusMinutes(10))
                .build();

        double priority = scheduler.calculateEffectivePriority(task);

        // 有效优先级 = 5 + (10 * 0.1) = 6.0
        assertEquals(6.0, priority, 0.1);
    }

    @Test
    void testCalculateEffectivePriority_LongWaitTime() {
        // 任务等待了100分钟
        GpuTask task = GpuTask.builder()
                .basePriority(3)
                .enqueueAt(LocalDateTime.now().minusMinutes(100))
                .build();

        double priority = scheduler.calculateEffectivePriority(task);

        // 有效优先级 = 3 + (100 * 0.1) = 13.0
        assertEquals(13.0, priority, 0.1);
    }

    @Test
    void testCalculateEffectivePriority_NoEnqueueTime() {
        // 任务没有入队时间（不应该发生，但要处理）
        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(null)
                .build();

        double priority = scheduler.calculateEffectivePriority(task);

        // 没有入队时间，返回基础优先级
        assertEquals(5.0, priority, 0.01);
    }

    @Test
    void testCalculateEffectivePriority_CustomAgeWeight() {
        // 使用自定义老化权重
        when(config.getAgeWeightPerMinute()).thenReturn(0.5);

        GpuTask task = GpuTask.builder()
                .basePriority(5)
                .enqueueAt(LocalDateTime.now().minusMinutes(10))
                .build();

        double priority = scheduler.calculateEffectivePriority(task);

        // 有效优先级 = 5 + (10 * 0.5) = 10.0
        assertEquals(10.0, priority, 0.1);
    }

    @Test
    void testRefreshTaskPriorities_Success() {
        // 执行刷新
        scheduler.refreshTaskPriorities();

        // 验证refreshScores被调用
        verify(priorityQueue, times(1)).refreshScores(any());
    }

    @Test
    void testRefreshTaskPriorities_AgingDisabled() {
        // 禁用老化机制
        when(config.isAgingEnabled()).thenReturn(false);

        // 执行刷新
        scheduler.refreshTaskPriorities();

        // 验证refreshScores未被调用
        verify(priorityQueue, never()).refreshScores(any());
    }

    @Test
    void testCalculateEffectivePriority_PreventStarvation() {
        // 低优先级任务等待很长时间后，优先级应该超过高优先级新任务
        GpuTask lowPriorityOldTask = GpuTask.builder()
                .basePriority(1)  // 最低优先级
                .enqueueAt(LocalDateTime.now().minusMinutes(100))  // 等待100分钟
                .build();

        GpuTask highPriorityNewTask = GpuTask.builder()
                .basePriority(10)  // 最高优先级
                .enqueueAt(LocalDateTime.now())  // 刚入队
                .build();

        double lowPriority = scheduler.calculateEffectivePriority(lowPriorityOldTask);
        double highPriority = scheduler.calculateEffectivePriority(highPriorityNewTask);

        // 低优先级任务: 1 + (100 * 0.1) = 11.0
        // 高优先级任务: 10 + (0 * 0.1) = 10.0
        // 低优先级任务应该超过高优先级任务
        assertTrue(lowPriority > highPriority,
                "低优先级老任务应该超过高优先级新任务，防止饥饿");
    }
}

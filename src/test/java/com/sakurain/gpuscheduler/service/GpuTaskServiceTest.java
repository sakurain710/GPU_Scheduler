package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.InvalidTaskStateException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GpuTaskService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class GpuTaskServiceTest {

    @Mock
    private GpuTaskMapper taskMapper;
    @Mock
    private GpuTaskLogMapper taskLogMapper;
    @Mock
    private TaskPriorityQueue priorityQueue;

    @InjectMocks
    private GpuTaskService gpuTaskService;

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    @BeforeEach
    void setUp() {
        // 用真实的状态机替换mock
        gpuTaskService = new GpuTaskService(taskMapper, taskLogMapper, stateMachine, priorityQueue);
    }

    @Test
    void submitTask_createsTaskAndEnqueues() {
        SubmitTaskRequest request = SubmitTaskRequest.builder()
                .title("训练ResNet50")
                .taskType("model_training")
                .minMemoryGb(new BigDecimal("24.00"))
                .computeUnitsGflop(new BigDecimal("500000.0000"))
                .basePriority(7)
                .build();

        // insert后模拟ID赋值
        doAnswer(inv -> {
            GpuTask t = inv.getArgument(0);
            t.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(GpuTask.class));

        // selectById: 第一次返回PENDING（transition内部查询），第二次返回QUEUED（submitTask最终查询）
        GpuTask pendingTask = GpuTask.builder()
                .id(100L).userId(1L).title("训练ResNet50")
                .taskType("model_training")
                .minMemoryGb(new BigDecimal("24.00"))
                .computeUnitsGflop(new BigDecimal("500000.0000"))
                .basePriority(7).status(TaskStatus.PENDING.getCode())
                .build();
        GpuTask queuedTask = GpuTask.builder()
                .id(100L).userId(1L).title("训练ResNet50")
                .taskType("model_training")
                .minMemoryGb(new BigDecimal("24.00"))
                .computeUnitsGflop(new BigDecimal("500000.0000"))
                .basePriority(7).status(TaskStatus.QUEUED.getCode())
                .build();
        when(taskMapper.selectById(100L)).thenReturn(pendingTask, queuedTask);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);

        TaskResponse response = gpuTaskService.submitTask(request, 1L);

        assertEquals(100L, response.getId());
        assertEquals("Queued", response.getStatusLabel());
        verify(priorityQueue).enqueue(eq(100L), eq(7.0));
        verify(taskLogMapper).insert(any(GpuTaskLog.class));
    }

    @Test
    void transition_cancelPendingTask() {
        GpuTask task = GpuTask.builder()
                .id(1L).status(TaskStatus.PENDING.getCode()).basePriority(5)
                .build();
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);

        gpuTaskService.transition(1L, TaskStatus.CANCELLED, null, 99L);

        ArgumentCaptor<GpuTask> captor = ArgumentCaptor.forClass(GpuTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(TaskStatus.CANCELLED.getCode(), captor.getValue().getStatus());
        assertNotNull(captor.getValue().getFinishedAt());

        // PENDING→CANCELLED不涉及队列操作
        verify(priorityQueue, never()).enqueue(anyLong(), anyDouble());
        verify(priorityQueue, never()).remove(anyLong());
    }

    @Test
    void transition_cancelQueuedTask_removesFromQueue() {
        GpuTask task = GpuTask.builder()
                .id(2L).status(TaskStatus.QUEUED.getCode()).basePriority(5)
                .build();
        when(taskMapper.selectById(2L)).thenReturn(task);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);

        gpuTaskService.transition(2L, TaskStatus.CANCELLED, null, 99L);

        verify(priorityQueue).remove(2L);
    }

    @Test
    void transition_invalidTransition_throws() {
        GpuTask task = GpuTask.builder()
                .id(3L).status(TaskStatus.COMPLETED.getCode())
                .build();
        when(taskMapper.selectById(3L)).thenReturn(task);

        assertThrows(InvalidTaskStateException.class,
                () -> gpuTaskService.transition(3L, TaskStatus.RUNNING, null, 99L));
    }

    @Test
    void transition_taskNotFound_throws() {
        when(taskMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> gpuTaskService.transition(999L, TaskStatus.QUEUED, null, 1L));
    }

    @Test
    void getTask_returnsResponse() {
        GpuTask task = GpuTask.builder()
                .id(1L).userId(1L).title("test").taskType("inference")
                .minMemoryGb(BigDecimal.ONE).computeUnitsGflop(BigDecimal.TEN)
                .basePriority(5).status(TaskStatus.QUEUED.getCode())
                .build();
        when(taskMapper.selectById(1L)).thenReturn(task);

        TaskResponse resp = gpuTaskService.getTask(1L);
        assertEquals(1L, resp.getId());
        assertEquals("Queued", resp.getStatusLabel());
    }

    @Test
    void getTask_notFound_throws() {
        when(taskMapper.selectById(999L)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> gpuTaskService.getTask(999L));
    }
}

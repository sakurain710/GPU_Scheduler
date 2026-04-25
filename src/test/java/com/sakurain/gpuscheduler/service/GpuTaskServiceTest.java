package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.config.TaskSubmissionPolicyConfig;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskAdminListItem;
import com.sakurain.gpuscheduler.dto.task.TaskExecutionLogResponse;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.InvalidTaskStateException;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler;
import com.sakurain.gpuscheduler.scheduler.TaskExecutionSimulator;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private GpuMapper gpuMapper;
    @Mock
    private GpuTaskLogMapper taskLogMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TaskPriorityQueue priorityQueue;
    @Mock
    private TaskAgingScheduler agingScheduler;
    @Mock
    private TaskNotificationService taskNotificationService;
    @Mock
    private TaskExecutionSimulator taskExecutionSimulator;

    private GpuTaskService gpuTaskService;

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    private final TaskSubmissionPolicyConfig submissionPolicy = new TaskSubmissionPolicyConfig();

    @BeforeEach
    void setUp() {
        gpuTaskService = new GpuTaskService(
                taskMapper,
                gpuMapper,
                taskLogMapper,
                userMapper,
                stateMachine,
                priorityQueue,
                agingScheduler,
                taskExecutionSimulator,
                submissionPolicy,
                taskNotificationService
        );

        lenient().when(agingScheduler.calculateEffectivePriority(any(GpuTask.class)))
                .thenAnswer(inv -> {
                    GpuTask task = inv.getArgument(0);
                    return (double) task.getBasePriority();
                });
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

        doAnswer(inv -> {
            GpuTask t = inv.getArgument(0);
            t.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(GpuTask.class));

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
        verify(taskNotificationService).notifyTaskStatus(eq(100L), eq(1L), eq(TaskStatus.PENDING), eq(TaskStatus.QUEUED), any());
    }

    @Test
    void transition_cancelQueuedTask_removesFromQueue() {
        GpuTask task = GpuTask.builder()
                .id(2L).userId(1L).status(TaskStatus.QUEUED.getCode()).basePriority(5)
                .build();
        when(taskMapper.selectById(2L)).thenReturn(task);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);

        gpuTaskService.transition(2L, TaskStatus.CANCELLED, null, 99L);

        verify(priorityQueue).remove(2L);
        verify(taskNotificationService).notifyTaskStatus(2L, 1L, TaskStatus.QUEUED, TaskStatus.CANCELLED, null);
        verify(taskExecutionSimulator, never()).cancelTask(anyLong());
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
    void drainQueuedTasks_cancelAllQueuedTasks() {
        GpuTask q1 = GpuTask.builder().id(101L).status(TaskStatus.QUEUED.getCode()).build();
        GpuTask q2 = GpuTask.builder().id(102L).status(TaskStatus.QUEUED.getCode()).build();
        when(taskMapper.selectList(any())).thenReturn(List.of(q1, q2));

        GpuTaskService spy = spy(gpuTaskService);
        doNothing().when(spy).transition(anyLong(), eq(TaskStatus.CANCELLED), eq(null), eq(9L));
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);

        int drained = spy.drainQueuedTasks(9L, "operator drain");

        assertEquals(2, drained);
        verify(spy, times(2)).transition(anyLong(), eq(TaskStatus.CANCELLED), eq(null), eq(9L));

        ArgumentCaptor<GpuTask> captor = ArgumentCaptor.forClass(GpuTask.class);
        verify(taskMapper, times(2)).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(v -> "operator drain".equals(v.getCancelReason())));
    }

    @Test
    void transition_runningToQueued_cancelsSimulatorTask() {
        GpuTask running = GpuTask.builder()
                .id(88L).userId(7L).status(TaskStatus.RUNNING.getCode()).basePriority(4)
                .build();
        when(taskMapper.selectById(88L)).thenReturn(running);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskExecutionSimulator.cancelTask(88L)).thenReturn(true);

        gpuTaskService.transition(88L, TaskStatus.QUEUED, null, 9L);

        verify(taskExecutionSimulator).cancelTask(88L);
        verify(priorityQueue).enqueue(eq(88L), anyDouble());
    }

    @Test
    void getTask_withNullTaskOwner_withoutApproverRole_forbidden() {
        GpuTask task = GpuTask.builder()
                .id(66L).userId(null).status(TaskStatus.QUEUED.getCode())
                .build();
        when(taskMapper.selectById(66L)).thenReturn(task);

        assertThrows(com.sakurain.gpuscheduler.exception.BusinessException.class,
                () -> gpuTaskService.getTask(66L, 1L, List.of()));

        verify(taskNotificationService, never()).notifyTaskStatus(anyLong(), any(), any(), any(), any());
        verify(taskExecutionSimulator, never()).cancelTask(anyLong());
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

    @Test
    void getTaskExecutionLogs_shouldReturnSummaryAndAscendingLogs() {
        LocalDateTime timestamp = LocalDateTime.now();
        GpuTask task = GpuTask.builder()
                .id(100L)
                .userId(1L)
                .gpuId(7L)
                .status(TaskStatus.RUNNING.getCode())
                .basePriority(6)
                .build();
        when(taskMapper.selectById(100L)).thenReturn(task);

        GpuTaskLog second = GpuTaskLog.builder()
                .id(2L)
                .taskId(100L)
                .event("DISPATCHED")
                .oldStatus(TaskStatus.QUEUED.getCode())
                .newStatus(TaskStatus.RUNNING.getCode())
                .operatorId(2L)
                .createdAt(timestamp)
                .build();
        GpuTaskLog first = GpuTaskLog.builder()
                .id(1L)
                .taskId(100L)
                .event("QUEUED")
                .oldStatus(TaskStatus.PENDING.getCode())
                .newStatus(TaskStatus.QUEUED.getCode())
                .operatorId(null)
                .createdAt(timestamp)
                .build();
        when(taskLogMapper.selectList(any())).thenReturn(List.of(second, first));
        when(gpuMapper.selectById(7L)).thenReturn(Gpu.builder().id(7L).name("NVIDIA A100 80G").build());

        User submitter = User.builder().id(1L).username("submitter").nickname("submitter_nick").build();
        User approver = User.builder().id(2L).username("reviewer").nickname("reviewer_nick").build();
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(submitter, approver));

        TaskExecutionLogResponse response = gpuTaskService.getTaskExecutionLogs(100L, 1L, List.of());

        assertEquals(100L, response.getTask().getId());
        assertEquals(7L, response.getTask().getGpuId());
        assertEquals("NVIDIA A100 80G", response.getTask().getGpuLabel());
        assertEquals(1L, response.getTask().getOperatorId());
        assertEquals("submitter_nick", response.getTask().getOperatorName());

        assertEquals(2, response.getLogs().size());
        assertEquals("QUEUED", response.getLogs().get(0).getEvent());
        assertEquals("Pending", response.getLogs().get(0).getOldStatusLabel());
        assertEquals("Queued", response.getLogs().get(0).getNewStatusLabel());
        assertNull(response.getLogs().get(0).getOperatorName());

        assertEquals("DISPATCHED", response.getLogs().get(1).getEvent());
        assertEquals("Queued", response.getLogs().get(1).getOldStatusLabel());
        assertEquals("Running", response.getLogs().get(1).getNewStatusLabel());
        assertEquals("reviewer_nick", response.getLogs().get(1).getOperatorName());
    }

    @Test
    void getTaskExecutionLogs_withEmptyLogs_shouldReturnEmptyList() {
        GpuTask task = GpuTask.builder()
                .id(200L)
                .userId(1L)
                .status(TaskStatus.QUEUED.getCode())
                .basePriority(5)
                .build();
        when(taskMapper.selectById(200L)).thenReturn(task);
        when(taskLogMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(1L).username("alice").build()));

        TaskExecutionLogResponse response = gpuTaskService.getTaskExecutionLogs(200L, 1L, List.of());

        assertNotNull(response.getTask());
        assertNotNull(response.getLogs());
        assertTrue(response.getLogs().isEmpty());
    }

    @Test
    void getTaskExecutionLogs_taskNotFound_shouldThrow() {
        when(taskMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> gpuTaskService.getTaskExecutionLogs(999L, 1L, List.of()));
    }

    @Test
    void getTaskExecutionLogs_withoutPermission_shouldThrow() {
        GpuTask task = GpuTask.builder()
                .id(300L)
                .userId(2L)
                .status(TaskStatus.QUEUED.getCode())
                .build();
        when(taskMapper.selectById(300L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gpuTaskService.getTaskExecutionLogs(300L, 1L, List.of()));
        assertEquals("TASK_FORBIDDEN", ex.getCode());
    }

    @Test
    void getTaskExecutionLogs_shouldFallbackToUsernameWhenNicknameBlank() {
        LocalDateTime timestamp = LocalDateTime.now();
        GpuTask task = GpuTask.builder()
                .id(400L)
                .userId(1L)
                .status(TaskStatus.QUEUED.getCode())
                .build();
        when(taskMapper.selectById(400L)).thenReturn(task);

        GpuTaskLog log = GpuTaskLog.builder()
                .id(10L)
                .taskId(400L)
                .event("QUEUED")
                .newStatus(TaskStatus.QUEUED.getCode())
                .operatorId(2L)
                .createdAt(timestamp)
                .build();
        when(taskLogMapper.selectList(any())).thenReturn(List.of(log));

        User submitter = User.builder().id(1L).username("submitter_username").nickname("  ").build();
        User operator = User.builder().id(2L).username("operator_username").nickname("operator_nick").build();
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(submitter, operator));

        TaskExecutionLogResponse response = gpuTaskService.getTaskExecutionLogs(400L, 1L, List.of());

        assertEquals("submitter_username", response.getTask().getOperatorName());
        assertEquals("operator_nick", response.getLogs().get(0).getOperatorName());
    }

    @Test
    void listAdminTasks_shouldReturnTaskSummaryPage() {
        GpuTask task = GpuTask.builder()
                .id(501L)
                .title("admin-task")
                .description("task-remark")
                .taskType("inference")
                .status(TaskStatus.RUNNING.getCode())
                .build();
        Page<GpuTask> page = new Page<>(1, 10);
        page.setRecords(List.of(task));
        page.setTotal(1L);
        when(taskMapper.selectPage(any(Page.class), any())).thenReturn(page);

        IPage<TaskAdminListItem> result = gpuTaskService.listAdminTasks(1, 10, "inference", TaskStatus.RUNNING.getCode(), "asc");

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        TaskAdminListItem item = result.getRecords().get(0);
        assertEquals(501L, item.getId());
        assertEquals("admin-task", item.getTitle());
        assertEquals("task-remark", item.getDescription());
        assertEquals("inference", item.getTaskType());
        assertEquals(TaskStatus.RUNNING.getCode(), item.getStatus());
        assertEquals("Running", item.getStatusLabel());
    }

    @Test
    void batchApproveTasks_shouldDeduplicateAndApprove() {
        GpuTaskService spy = spy(gpuTaskService);
        doReturn(TaskResponse.builder().id(10L).status(TaskStatus.QUEUED.getCode()).statusLabel("Queued").build())
                .when(spy).approveTask(10L, 9L);
        doReturn(TaskResponse.builder().id(11L).status(TaskStatus.QUEUED.getCode()).statusLabel("Queued").build())
                .when(spy).approveTask(11L, 9L);

        List<TaskResponse> result = spy.batchApproveTasks(List.of(10L, 10L, 11L), 9L);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).getId());
        assertEquals(11L, result.get(1).getId());
        verify(spy, times(1)).approveTask(10L, 9L);
        verify(spy, times(1)).approveTask(11L, 9L);
    }

    @Test
    void batchRejectTasks_withInvalidTaskId_shouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gpuTaskService.batchRejectTasks(List.of(1L, 0L), 9L, "invalid"));
        assertEquals("TASK_BATCH_ID_INVALID", ex.getCode());
    }
}

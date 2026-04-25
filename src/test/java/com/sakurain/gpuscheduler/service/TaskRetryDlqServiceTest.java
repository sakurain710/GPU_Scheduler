package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.config.TaskRetryPolicyConfig;
import com.sakurain.gpuscheduler.entity.TaskDlq;
import com.sakurain.gpuscheduler.enums.TaskLogEvent;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.mapper.TaskDlqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRetryDlqServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private GpuTaskMapper taskMapper;
    @Mock
    private TaskDlqMapper taskDlqMapper;
    @Mock
    private GpuTaskService taskService;

    private TaskRetryDlqService retryDlqService;

    @BeforeEach
    void setUp() {
        TaskRetryPolicyConfig policy = new TaskRetryPolicyConfig();
        retryDlqService = new TaskRetryDlqService(
                redisTemplate,
                policy,
                taskMapper,
                taskDlqMapper,
                taskService
        );
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void reprocessDlqTask_shouldNotMatchSubstringTaskId() {
        when(taskDlqMapper.selectOne(any())).thenReturn(null);

        boolean reprocessed = retryDlqService.reprocessDlqTask(12L);

        assertFalse(reprocessed);
        verify(taskDlqMapper, never()).updateById(any(TaskDlq.class));
        verify(taskService, never()).transition(anyLong(), eq(TaskStatus.QUEUED), any(), any());
    }

    @Test
    void reprocessDlqTask_shouldRequeueWhenTaskIdExactlyMatches() {
        when(taskDlqMapper.selectOne(any())).thenReturn(TaskDlq.builder()
                .id(1L)
                .taskId(12L)
                .retryCount(2)
                .failureReason("x")
                .status(1)
                .build());

        boolean reprocessed = retryDlqService.reprocessDlqTask(12L);

        assertTrue(reprocessed);
        verify(hashOperations).delete("gpu:task:retry:count", "12");
        verify(taskService).transition(12L, TaskStatus.QUEUED, null, null, TaskLogEvent.DLQ_REPROCESSED, "x");
        verify(taskDlqMapper).updateById(any(TaskDlq.class));
    }

    @Test
    void reprocessDlqTask_shouldKeepPendingWhenRequeueFails() {
        when(taskDlqMapper.selectOne(any())).thenReturn(TaskDlq.builder()
                .id(1L)
                .taskId(12L)
                .retryCount(2)
                .failureReason("x")
                .status(1)
                .build());
        doThrow(new RuntimeException("transition failed"))
                .when(taskService)
                .transition(12L, TaskStatus.QUEUED, null, null, TaskLogEvent.DLQ_REPROCESSED, "x");

        boolean reprocessed = retryDlqService.reprocessDlqTask(12L);

        assertFalse(reprocessed);
        verify(hashOperations).delete("gpu:task:retry:count", "12");
        verify(hashOperations, never()).increment(eq("gpu:task:retry:count"), eq("12"), anyLong());
        verify(taskDlqMapper, never()).insert(any(TaskDlq.class));
        verify(taskDlqMapper, never()).updateById(any(TaskDlq.class));
    }
}

package com.sakurain.gpuscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.config.TaskRetryPolicyConfig;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRetryDlqServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private GpuTaskMapper taskMapper;
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
                taskService,
                new ObjectMapper()
        );
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void reprocessDlqTask_shouldNotMatchSubstringTaskId() {
        when(listOperations.range("gpu:task:dlq", 0, 999)).thenReturn(
                List.of("{\"taskId\":112,\"attempt\":1,\"reason\":\"x\",\"time\":\"t\"}")
        );

        boolean reprocessed = retryDlqService.reprocessDlqTask(12L);

        assertFalse(reprocessed);
        verify(listOperations, never()).remove(eq("gpu:task:dlq"), eq(1L), any());
        verify(taskService, never()).transition(anyLong(), eq(TaskStatus.QUEUED), any(), any());
    }

    @Test
    void reprocessDlqTask_shouldRequeueWhenTaskIdExactlyMatches() {
        String payload = "{\"taskId\":12,\"attempt\":2,\"reason\":\"x\",\"time\":\"t\"}";
        when(listOperations.range("gpu:task:dlq", 0, 999)).thenReturn(List.of(payload));
        when(listOperations.remove("gpu:task:dlq", 1, payload)).thenReturn(1L);

        boolean reprocessed = retryDlqService.reprocessDlqTask(12L);

        assertTrue(reprocessed);
        verify(hashOperations).delete("gpu:task:retry:count", "12");
        verify(taskService).transition(12L, TaskStatus.QUEUED, null, null);
    }
}

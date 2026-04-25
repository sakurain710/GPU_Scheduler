package com.sakurain.gpuscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.task.TaskStatusNotification;
import com.sakurain.gpuscheduler.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskNotificationServiceTest {

    private static final String WEBHOOK_RETRY_QUEUE = "gpu:notify:webhook:retry";

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;
    @Mock
    private TaskDashboardPushService taskDashboardPushService;
    @Mock
    private NotificationMapper notificationMapper;

    private TaskNotificationService service;

    @BeforeEach
    void setUp() {
        service = new TaskNotificationService(
                messagingTemplate,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                taskDashboardPushService,
                notificationMapper
        );
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    void enqueueWebhookRetry_shouldTrimRetryQueueToConfiguredMaxSize() {
        ReflectionTestUtils.setField(service, "webhookRetryQueueMaxSize", 1000);

        ReflectionTestUtils.invokeMethod(service, "enqueueWebhookRetry", payload(), 1);

        verify(listOperations).leftPush(eq(WEBHOOK_RETRY_QUEUE), anyString());
        verify(listOperations).trim(WEBHOOK_RETRY_QUEUE, 0, 999);
    }

    @Test
    void enqueueWebhookRetry_shouldNormalizeInvalidMaxSizeToOne() {
        ReflectionTestUtils.setField(service, "webhookRetryQueueMaxSize", 0);

        ReflectionTestUtils.invokeMethod(service, "enqueueWebhookRetry", payload(), 1);

        verify(listOperations).leftPush(eq(WEBHOOK_RETRY_QUEUE), anyString());
        verify(listOperations).trim(WEBHOOK_RETRY_QUEUE, 0, 0);
    }

    @Test
    void webhookRetryQueueSize_shouldReturnZeroWhenRedisReturnsNull() {
        when(listOperations.size(WEBHOOK_RETRY_QUEUE)).thenReturn(null);

        long size = service.webhookRetryQueueSize();

        assertThat(size).isZero();
    }

    private TaskStatusNotification payload() {
        return TaskStatusNotification.builder()
                .taskId(1L)
                .userId(2L)
                .fromStatus("Running")
                .toStatus("Failed")
                .occurredAt(LocalDateTime.now())
                .message("x")
                .build();
    }
}

package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.task.TaskStatusNotification;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * 任务状态通知服务（WebSocket + Webhook）
 */
@Slf4j
@Service
public class TaskNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;

    @Value("${notification.task-topic-prefix:/topic/task-status/}")
    private String taskTopicPrefix;

    @Value("${notification.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${notification.webhook.url:}")
    private String webhookUrl;

    @Value("${notification.webhook.timeout-ms:2000}")
    private int webhookTimeoutMs;

    public TaskNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.restTemplate = new RestTemplate();
    }

    public void notifyTaskStatus(Long taskId,
                                 Long userId,
                                 TaskStatus from,
                                 TaskStatus to,
                                 String message) {
        if (userId == null) {
            return;
        }
        TaskStatusNotification payload = TaskStatusNotification.builder()
                .taskId(taskId)
                .userId(userId)
                .fromStatus(from.getLabel())
                .toStatus(to.getLabel())
                .occurredAt(LocalDateTime.now())
                .message(message)
                .build();

        pushWebSocket(payload);
        pushWebhook(payload);
    }

    private void pushWebSocket(TaskStatusNotification payload) {
        String topic = taskTopicPrefix + payload.getUserId();
        messagingTemplate.convertAndSend(topic, payload);
    }

    private void pushWebhook(TaskStatusNotification payload) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<TaskStatusNotification> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhookUrl, request, String.class);
        } catch (Exception ex) {
            log.warn("Webhook通知失败: {}", ex.getMessage());
        }
    }
}


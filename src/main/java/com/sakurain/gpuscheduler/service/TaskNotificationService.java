package com.sakurain.gpuscheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.task.TaskStatusNotification;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务状态通知服务（WebSocket + Webhook）
 */
@Slf4j
@Service
public class TaskNotificationService {

    private static final String WEBHOOK_RETRY_QUEUE = "gpu:notify:webhook:retry";

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.task-topic-prefix:/topic/task-status/}")
    private String taskTopicPrefix;

    @Value("${notification.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${notification.webhook.url:}")
    private String webhookUrl;

    @Value("${notification.webhook.timeout-ms:2000}")
    private int webhookTimeoutMs;

    @Value("${notification.webhook.retry-max-attempts:3}")
    private int webhookRetryMaxAttempts;

    @Value("${notification.webhook.retry-interval-ms:10000}")
    private long webhookRetryIntervalMs;

    @Value("${notification.webhook.retry-batch-size:50}")
    private int webhookRetryBatchSize;

    public TaskNotificationService(SimpMessagingTemplate messagingTemplate,
                                   RedisTemplate<String, String> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
        pushWebhook(payload, 1);
    }

    @Scheduled(fixedDelayString = "${notification.webhook.retry-interval-ms:10000}")
    public void retryWebhookQueue() {
        if (!webhookEnabled || isWebhookDisabled()) {
            return;
        }
        for (int i = 0; i < webhookRetryBatchSize; i++) {
            String item = redisTemplate.opsForList().rightPop(WEBHOOK_RETRY_QUEUE);
            if (item == null) {
                break;
            }
            try {
                RetryEnvelope envelope = objectMapper.readValue(item, RetryEnvelope.class);
                pushWebhook(envelope.payload, envelope.attempt + 1);
            } catch (Exception ex) {
                log.warn("Webhook重试消息解析失败: {}", ex.getMessage());
            }
        }
    }

    public long webhookRetryQueueSize() {
        Long size = redisTemplate.opsForList().size(WEBHOOK_RETRY_QUEUE);
        return size != null ? size : 0L;
    }

    private void pushWebSocket(TaskStatusNotification payload) {
        String topic = taskTopicPrefix + payload.getUserId();
        messagingTemplate.convertAndSend(topic, payload);
    }

    private void pushWebhook(TaskStatusNotification payload, int attempt) {
        if (!webhookEnabled || isWebhookDisabled()) {
            return;
        }

        RestTemplate template = buildRestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<TaskStatusNotification> request = new HttpEntity<>(payload, headers);
            template.postForEntity(webhookUrl, request, String.class);
        } catch (Exception ex) {
            if (attempt >= webhookRetryMaxAttempts) {
                log.warn("Webhook通知失败且超过重试上限: attempt={}, err={}", attempt, ex.getMessage());
                return;
            }
            enqueueWebhookRetry(payload, attempt);
        }
    }

    private RestTemplate buildRestTemplate() {
        int timeout = Math.max(500, webhookTimeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void enqueueWebhookRetry(TaskStatusNotification payload, int attempt) {
        try {
            String body = objectMapper.writeValueAsString(new RetryEnvelope(payload, attempt));
            redisTemplate.opsForList().leftPush(WEBHOOK_RETRY_QUEUE, body);
        } catch (JsonProcessingException e) {
            log.warn("Webhook重试入队失败: {}", e.getMessage());
        }
    }

    private boolean isWebhookDisabled() {
        return webhookUrl == null || webhookUrl.isBlank();
    }

    private static class RetryEnvelope {
        public TaskStatusNotification payload;
        public int attempt;

        public RetryEnvelope() {
        }

        public RetryEnvelope(TaskStatusNotification payload, int attempt) {
            this.payload = payload;
            this.attempt = attempt;
        }
    }
}

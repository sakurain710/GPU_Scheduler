package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.config.TaskRetryPolicyConfig;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 失败任务重试与死信队列
 */
@Slf4j
@Service
public class TaskRetryDlqService {

    private static final String RETRY_COUNT_KEY = "gpu:task:retry:count";
    private static final String RETRY_SCHEDULE_KEY = "gpu:task:retry:schedule";
    private static final String DLQ_KEY = "gpu:task:dlq";

    private final RedisTemplate<String, String> redisTemplate;
    private final TaskRetryPolicyConfig retryPolicy;
    private final GpuTaskMapper taskMapper;
    private final GpuTaskService taskService;

    public TaskRetryDlqService(RedisTemplate<String, String> redisTemplate,
                               TaskRetryPolicyConfig retryPolicy,
                               GpuTaskMapper taskMapper,
                               GpuTaskService taskService) {
        this.redisTemplate = redisTemplate;
        this.retryPolicy = retryPolicy;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
    }

    /**
     * 失败任务进入重试流程，超过阈值进入死信队列
     */
    public void onTaskFailed(Long taskId, String reason) {
        if (!retryPolicy.isEnabled()) {
            pushToDlq(taskId, reason, 0);
            return;
        }

        Long attempt = redisTemplate.opsForHash().increment(RETRY_COUNT_KEY, taskId.toString(), 1L);
        long currentAttempt = attempt != null ? attempt : 1L;
        if (currentAttempt > retryPolicy.getMaxRetries()) {
            pushToDlq(taskId, reason, currentAttempt);
            return;
        }

        long delaySeconds = (long) (retryPolicy.getInitialBackoffSeconds() * Math.pow(2, currentAttempt - 1));
        long executeAtMs = System.currentTimeMillis() + delaySeconds * 1000;
        redisTemplate.opsForZSet().add(RETRY_SCHEDULE_KEY, taskId.toString(), executeAtMs);
        log.info("任务{}进入重试队列: attempt={}, delaySeconds={}", taskId, currentAttempt, delaySeconds);
    }

    /**
     * 定时扫描到期重试任务
     */
    @Scheduled(fixedDelayString = "${task-retry.scan-interval-ms:5000}")
    public void processScheduledRetries() {
        if (!retryPolicy.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> dueTaskIds = redisTemplate.opsForZSet()
                .rangeByScore(RETRY_SCHEDULE_KEY, 0, now, 0, retryPolicy.getBatchSize());
        if (dueTaskIds == null || dueTaskIds.isEmpty()) {
            return;
        }

        for (String taskIdStr : dueTaskIds) {
            Long removed = redisTemplate.opsForZSet().remove(RETRY_SCHEDULE_KEY, taskIdStr);
            if (removed == null || removed == 0) {
                continue;
            }

            Long taskId = Long.valueOf(taskIdStr);
            GpuTask task = taskMapper.selectById(taskId);
            if (task == null) {
                continue;
            }
            if (task.getStatus() != TaskStatus.FAILED.getCode()) {
                continue;
            }

            try {
                taskService.transition(taskId, TaskStatus.QUEUED, null, null);
            } catch (Exception ex) {
                log.warn("任务{}重试重入队失败: {}", taskId, ex.getMessage());
                onTaskFailed(taskId, ex.getMessage());
            }
        }
    }

    public List<String> listDlq(int limit) {
        return redisTemplate.opsForList().range(DLQ_KEY, 0, Math.max(0, limit - 1));
    }

    public long retryQueueSize() {
        Long size = redisTemplate.opsForZSet().size(RETRY_SCHEDULE_KEY);
        return size != null ? size : 0L;
    }

    public long dlqSize() {
        Long size = redisTemplate.opsForList().size(DLQ_KEY);
        return size != null ? size : 0L;
    }

    public long clearDlq() {
        Boolean removed = redisTemplate.delete(DLQ_KEY);
        return Boolean.TRUE.equals(removed) ? 1L : 0L;
    }

    /**
     * 从死信队列中移除并重入队指定任务
     */
    public boolean reprocessDlqTask(Long taskId) {
        List<String> items = listDlq(1000);
        if (items == null || items.isEmpty()) {
            return false;
        }
        String marker = "\"taskId\":" + taskId;
        for (String item : items) {
            if (item != null && item.contains(marker)) {
                Long removed = redisTemplate.opsForList().remove(DLQ_KEY, 1, item);
                if (removed != null && removed > 0) {
                    redisTemplate.opsForHash().delete(RETRY_COUNT_KEY, taskId.toString());
                    try {
                        taskService.transition(taskId, TaskStatus.QUEUED, null, null);
                        return true;
                    } catch (Exception ex) {
                        onTaskFailed(taskId, ex.getMessage());
                    }
                }
            }
        }
        return false;
    }

    private void pushToDlq(Long taskId, String reason, long attempt) {
        String payload = String.format(
                "{\"taskId\":%d,\"attempt\":%d,\"reason\":\"%s\",\"time\":\"%s\"}",
                taskId,
                attempt,
                sanitize(reason),
                LocalDateTime.now()
        );
        redisTemplate.opsForList().leftPush(DLQ_KEY, payload);
        redisTemplate.opsForZSet().remove(RETRY_SCHEDULE_KEY, taskId.toString());
        log.warn("任务{}进入死信队列: attempt={}, reason={}", taskId, attempt, reason);
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\"", "'");
    }
}

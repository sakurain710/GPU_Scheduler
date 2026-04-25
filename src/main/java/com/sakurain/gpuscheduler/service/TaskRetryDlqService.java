package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.config.TaskRetryPolicyConfig;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.TaskDlq;
import com.sakurain.gpuscheduler.enums.TaskLogEvent;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.mapper.TaskDlqMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int DLQ_STATUS_PENDING = 1;
    private static final int DLQ_STATUS_REPROCESSED = 2;
    private static final int DLQ_STATUS_IGNORED = 3;

    private final RedisTemplate<String, String> redisTemplate;
    private final TaskRetryPolicyConfig retryPolicy;
    private final GpuTaskMapper taskMapper;
    private final TaskDlqMapper taskDlqMapper;
    private final GpuTaskService taskService;
    private final ObjectMapper objectMapper;
    @Value("${scheduler.scheduled-jobs-enabled:true}")
    private boolean scheduledJobsEnabled;

    public TaskRetryDlqService(RedisTemplate<String, String> redisTemplate,
                               TaskRetryPolicyConfig retryPolicy,
                               GpuTaskMapper taskMapper,
                               TaskDlqMapper taskDlqMapper,
                               GpuTaskService taskService,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.retryPolicy = retryPolicy;
        this.taskMapper = taskMapper;
        this.taskDlqMapper = taskDlqMapper;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
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
        if (!scheduledJobsEnabled || !retryPolicy.isEnabled()) {
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
        int normalizedLimit = Math.max(1, limit);
        return taskDlqMapper.selectList(new LambdaQueryWrapper<TaskDlq>()
                        .eq(TaskDlq::getStatus, DLQ_STATUS_PENDING)
                        .orderByDesc(TaskDlq::getCreatedAt)
                        .last("LIMIT " + normalizedLimit))
                .stream()
                .map(this::toDlqPayload)
                .toList();
    }

    public long retryQueueSize() {
        Long size = redisTemplate.opsForZSet().size(RETRY_SCHEDULE_KEY);
        return size != null ? size : 0L;
    }

    public long dlqSize() {
        Long size = taskDlqMapper.selectCount(new LambdaQueryWrapper<TaskDlq>()
                .eq(TaskDlq::getStatus, DLQ_STATUS_PENDING));
        return size != null ? size : 0L;
    }

    public long clearDlq() {
        Long pending = dlqSize();
        if (pending == 0) {
            return 0L;
        }
        taskDlqMapper.update(null, new LambdaUpdateWrapper<TaskDlq>()
                .eq(TaskDlq::getStatus, DLQ_STATUS_PENDING)
                .set(TaskDlq::getStatus, DLQ_STATUS_IGNORED)
                .set(TaskDlq::getProcessedAt, LocalDateTime.now()));
        return pending;
    }

    /**
     * 从死信队列中移除并重入队指定任务
     */
    public boolean reprocessDlqTask(Long taskId) {
        return reprocessDlqTask(taskId, null);
    }

    public boolean reprocessDlqTask(Long taskId, Long operatorId) {
        TaskDlq dlq = taskDlqMapper.selectOne(new LambdaQueryWrapper<TaskDlq>()
                .eq(TaskDlq::getTaskId, taskId)
                .eq(TaskDlq::getStatus, DLQ_STATUS_PENDING)
                .orderByDesc(TaskDlq::getCreatedAt)
                .last("LIMIT 1"));
        if (dlq == null) {
            return false;
        }
        redisTemplate.opsForHash().delete(RETRY_COUNT_KEY, taskId.toString());
        try {
            taskService.transition(taskId, TaskStatus.QUEUED, null, null,
                    TaskLogEvent.DLQ_REPROCESSED, dlq.getFailureReason());
            markDlq(dlq, DLQ_STATUS_REPROCESSED, operatorId);
            return true;
        } catch (Exception ex) {
            onTaskFailed(taskId, ex.getMessage());
        }
        return false;
    }

    private Long extractTaskId(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.has("taskId") || !root.get("taskId").canConvertToLong()) {
                return null;
            }
            return root.get("taskId").longValue();
        } catch (Exception ex) {
            return null;
        }
    }

    private void pushToDlq(Long taskId, String reason, long attempt) {
        String payload = String.format(
                "{\"taskId\":%d,\"attempt\":%d,\"reason\":\"%s\",\"time\":\"%s\"}",
                taskId,
                attempt,
                sanitize(reason),
                LocalDateTime.now()
        );
        taskDlqMapper.insert(TaskDlq.builder()
                .taskId(taskId)
                .retryCount((int) Math.min(attempt, Integer.MAX_VALUE))
                .failureReason(sanitize(reason))
                .payload(payload)
                .status(DLQ_STATUS_PENDING)
                .build());
        redisTemplate.opsForZSet().remove(RETRY_SCHEDULE_KEY, taskId.toString());
        GpuTask task = taskMapper.selectById(taskId);
        if (task != null) {
            taskService.appendAudit(taskId, task.getGpuId(), TaskLogEvent.DLQ_ENTERED,
                    TaskStatus.fromCode(task.getStatus()), TaskStatus.fromCode(task.getStatus()),
                    null, reason);
        }
        log.warn("任务{}进入死信队列: attempt={}, reason={}", taskId, attempt, reason);
    }

    private void markDlq(TaskDlq dlq, int status, Long operatorId) {
        TaskDlq update = new TaskDlq();
        update.setId(dlq.getId());
        update.setStatus(status);
        update.setProcessedBy(operatorId);
        update.setProcessedAt(LocalDateTime.now());
        taskDlqMapper.updateById(update);
    }

    private String toDlqPayload(TaskDlq dlq) {
        if (dlq.getPayload() != null && !dlq.getPayload().isBlank()) {
            return dlq.getPayload();
        }
        return String.format(
                "{\"taskId\":%d,\"attempt\":%d,\"reason\":\"%s\",\"time\":\"%s\"}",
                dlq.getTaskId(),
                dlq.getRetryCount() == null ? 0 : dlq.getRetryCount(),
                sanitize(dlq.getFailureReason()),
                dlq.getCreatedAt()
        );
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\"", "'");
    }
}

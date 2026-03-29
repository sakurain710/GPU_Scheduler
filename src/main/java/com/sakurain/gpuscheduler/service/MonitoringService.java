package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.dto.monitor.GpuMetrics;
import com.sakurain.gpuscheduler.dto.monitor.SystemHealth;
import com.sakurain.gpuscheduler.dto.monitor.TaskMetrics;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.CircuitBreakerService;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 监控与可观测性服务
 * <p>
 * 聚合任务指标、GPU 指标和系统健康状态，供 MonitoringController 使用。
 */
@Slf4j
@Service
public class MonitoringService {

    private final GpuTaskMapper gpuTaskMapper;
    private final GpuMapper gpuMapper;
    private final TaskPriorityQueue priorityQueue;
    private final CircuitBreakerService circuitBreaker;
    private final RedisTemplate<String, String> redisTemplate;
    private final TaskRetryDlqService retryDlqService;
    private final TaskNotificationService taskNotificationService;

    public MonitoringService(GpuTaskMapper gpuTaskMapper,
                             GpuMapper gpuMapper,
                             TaskPriorityQueue priorityQueue,
                             CircuitBreakerService circuitBreaker,
                             RedisTemplate<String, String> redisTemplate,
                             TaskRetryDlqService retryDlqService,
                             TaskNotificationService taskNotificationService) {
        this.gpuTaskMapper = gpuTaskMapper;
        this.gpuMapper = gpuMapper;
        this.priorityQueue = priorityQueue;
        this.circuitBreaker = circuitBreaker;
        this.redisTemplate = redisTemplate;
        this.retryDlqService = retryDlqService;
        this.taskNotificationService = taskNotificationService;
    }

    // ── Task Metrics ──────────────────────────────────────────────────────────

    /**
     * 聚合任务指标：队列长度、状态分布、等待时间、完成率、失败率
     */
    public TaskMetrics getTaskMetrics() {
        List<GpuTask> allTasks = gpuTaskMapper.selectList(null);
        LocalDateTime now = LocalDateTime.now();

        // 各状态任务数
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (TaskStatus s : TaskStatus.values()) {
            countByStatus.put(s.getLabel(), 0L);
        }
        countByStatus.putAll(allTasks.stream()
                .collect(Collectors.groupingBy(
                        t -> TaskStatus.fromCode(t.getStatus()).getLabel(),
                        Collectors.counting())));

        Map<String, Long> queueLengthByPriority = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.QUEUED.getCode())
                .collect(Collectors.groupingBy(
                        t -> priorityLabel(t.getBasePriority()),
                        LinkedHashMap::new,
                        Collectors.counting()));

        // 各优先级平均等待时间（仅 QUEUED 任务，等待时间 = now - enqueueAt）
        Map<String, Double> avgWaitByPriority = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.QUEUED.getCode() && t.getEnqueueAt() != null)
                .collect(Collectors.groupingBy(
                        t -> priorityLabel(t.getBasePriority()),
                        LinkedHashMap::new,
                        Collectors.averagingDouble(t ->
                                Duration.between(t.getEnqueueAt(), now).toSeconds())));

        Double avgDispatchLatency = allTasks.stream()
                .filter(t -> t.getEnqueueAt() != null && t.getDispatchedAt() != null)
                .mapToLong(t -> Duration.between(t.getEnqueueAt(), t.getDispatchedAt()).toSeconds())
                .average()
                .orElse(0.0);
        List<Long> dispatchLatencies = allTasks.stream()
                .filter(t -> t.getEnqueueAt() != null && t.getDispatchedAt() != null)
                .map(t -> Duration.between(t.getEnqueueAt(), t.getDispatchedAt()).toSeconds())
                .sorted()
                .toList();

        Double avgTurnaround = allTasks.stream()
                .filter(t -> t.getEnqueueAt() != null && t.getFinishedAt() != null)
                .mapToLong(t -> Duration.between(t.getEnqueueAt(), t.getFinishedAt()).toSeconds())
                .average()
                .orElse(0.0);

        Map<String, Double> dispatchPercentiles = new LinkedHashMap<>();
        dispatchPercentiles.put("p50", percentile(dispatchLatencies, 0.50));
        dispatchPercentiles.put("p95", percentile(dispatchLatencies, 0.95));
        dispatchPercentiles.put("p99", percentile(dispatchLatencies, 0.99));

        Map<String, Long> queueAgeHistogram = initQueueAgeHistogram();
        allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.QUEUED.getCode() && t.getEnqueueAt() != null)
                .forEach(t -> {
                    long ageSeconds = Duration.between(t.getEnqueueAt(), now).toSeconds();
                    String bucket = queueAgeBucket(ageSeconds);
                    queueAgeHistogram.put(bucket, queueAgeHistogram.getOrDefault(bucket, 0L) + 1L);
                });

        // 终态任务：完成率 / 失败率
        long completed = countByStatus.getOrDefault("Completed", 0L);
        long failed = countByStatus.getOrDefault("Failed", 0L);
        long cancelled = countByStatus.getOrDefault("Cancelled", 0L);
        long terminal = completed + failed + cancelled;

        String completionRate = terminal == 0 ? "0.0%" :
                String.format("%.1f%%", (double) completed / terminal * 100);
        String failureRate = terminal == 0 ? "0.0%" :
                String.format("%.1f%%", (double) failed / terminal * 100);

        // 失败原因分布（取 errorMessage 前 50 字符作为 key）
        Map<String, Long> failureReasons = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED.getCode()
                        && t.getErrorMessage() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getErrorMessage().length() > 50
                                ? t.getErrorMessage().substring(0, 50)
                                : t.getErrorMessage(),
                        Collectors.counting()));
        Map<String, Long> allocationFailureReasons = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED.getCode())
                .map(GpuTask::getErrorMessage)
                .filter(m -> m != null && !m.isBlank())
                .map(this::classifyAllocationFailure)
                .filter(m -> !"NOT_ALLOCATION".equals(m))
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()));

        Map<Long, Double> userSlaCompliancePct = calculateUserSlaCompliance(allTasks);

        long pendingApprovalCount = countByStatus.getOrDefault(TaskStatus.PENDING_APPROVAL.getLabel(), 0L);

        return TaskMetrics.builder()
                .queueLength(priorityQueue.size())
                .queueLengthByPriority(queueLengthByPriority)
                .taskCountByStatus(countByStatus)
                .avgWaitSecondsByPriority(avgWaitByPriority)
                .avgDispatchLatencySeconds(avgDispatchLatency)
                .avgTurnaroundSeconds(avgTurnaround)
                .dispatchLatencyPercentilesSeconds(dispatchPercentiles)
                .queueAgeHistogram(queueAgeHistogram)
                .completionRate(completionRate)
                .failureRate(failureRate)
                .failureReasons(failureReasons)
                .allocationFailureReasons(allocationFailureReasons)
                .userSlaCompliancePct(userSlaCompliancePct)
                .retryQueueSize(retryDlqService.retryQueueSize())
                .dlqSize(retryDlqService.dlqSize())
                .pendingApprovalCount(pendingApprovalCount)
                .webhookRetryQueueSize(taskNotificationService.webhookRetryQueueSize())
                .build();
    }

    // ── GPU Metrics ───────────────────────────────────────────────────────────

    /**
     * 聚合 GPU 指标：利用率、VRAM 碎片化、空闲时长
     */
    public GpuMetrics getGpuMetrics() {
        List<Gpu> allGpus = gpuMapper.selectList(null);
        long total = allGpus.size();

        // 各状态数量
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (GpuStatus s : GpuStatus.values()) {
            countByStatus.put(s.getLabel(), 0L);
        }
        countByStatus.putAll(allGpus.stream()
                .collect(Collectors.groupingBy(
                        g -> GpuStatus.fromCode(g.getStatus()).getLabel(),
                        Collectors.counting())));

        long busy = countByStatus.getOrDefault("Busy", 0L);
        String utilizationRate = total == 0 ? "0.0%" :
                String.format("%.1f%%", (double) busy / total * 100);

        // VRAM 碎片化：对每个 BUSY GPU，查找其当前任务的 minMemoryGb，
        // fragmentation = 1 - (minMemoryGb / gpu.memoryGb)
        // 值越高说明分配越浪费（大 GPU 跑小任务）
        List<GpuTask> runningTasks = gpuTaskMapper.selectList(
                new LambdaQueryWrapper<GpuTask>()
                        .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
                        .isNotNull(GpuTask::getGpuId));

        Map<Long, GpuTask> taskByGpuId = runningTasks.stream()
                .collect(Collectors.toMap(GpuTask::getGpuId, t -> t, (a, b) -> a));

        Map<Long, BigDecimal> usedMemory = new LinkedHashMap<>();
        Map<Long, BigDecimal> remainingMemory = new LinkedHashMap<>();
        Map<Long, BigDecimal> fragmentation = new LinkedHashMap<>();
        Map<Long, Long> idleSeconds = new LinkedHashMap<>();

        for (Gpu gpu : allGpus) {
            GpuTask runningTask = taskByGpuId.get(gpu.getId());
            BigDecimal used = runningTask != null && runningTask.getMinMemoryGb() != null
                    ? runningTask.getMinMemoryGb()
                    : BigDecimal.ZERO;
            usedMemory.put(gpu.getId(), used);

            BigDecimal remain = gpu.getMemoryGb().subtract(used);
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                remain = BigDecimal.ZERO;
            }
            remainingMemory.put(gpu.getId(), remain);

            if (gpu.getStatus().equals(GpuStatus.BUSY.getCode())
                    && gpu.getMemoryGb().compareTo(BigDecimal.ZERO) > 0
                    && runningTask != null
                    && runningTask.getMinMemoryGb() != null) {
                BigDecimal ratio = BigDecimal.ONE.subtract(
                        runningTask.getMinMemoryGb().divide(gpu.getMemoryGb(), 4, RoundingMode.HALF_UP));
                fragmentation.put(gpu.getId(), ratio.max(BigDecimal.ZERO));
            }

        // 空闲时长：对每个 IDLE GPU，查找其最近一次完成任务的 finishedAt
            if (gpu.getStatus().equals(GpuStatus.IDLE.getCode())) {
                GpuTask lastTask = gpuTaskMapper.selectOne(
                        new LambdaQueryWrapper<GpuTask>()
                                .eq(GpuTask::getGpuId, gpu.getId())
                                .isNotNull(GpuTask::getFinishedAt)
                                .orderByDesc(GpuTask::getFinishedAt)
                                .last("LIMIT 1"));
                if (lastTask != null && lastTask.getFinishedAt() != null) {
                    idleSeconds.put(gpu.getId(),
                            Duration.between(lastTask.getFinishedAt(), LocalDateTime.now()).toSeconds());
                }
            }
        }

        return GpuMetrics.builder()
                .total(total)
                .countByStatus(countByStatus)
                .utilizationRate(utilizationRate)
                .usedMemoryGbByGpu(usedMemory)
                .remainingMemoryGbByGpu(remainingMemory)
                .vramFragmentationByGpu(fragmentation)
                .idleSecondsByGpu(idleSeconds)
                .build();
    }

    // ── System Health ─────────────────────────────────────────────────────────

    /**
     * 系统整体健康检查
     */
    public SystemHealth getSystemHealth() {
        String dbStatus = checkDb();
        String redisStatus = checkRedis();
        String cbState = circuitBreaker.getState().name();

        // 队列中最老任务的等待时间
        long oldestWait = -1L;
        Set<String> members = priorityQueue.allMembers();
        if (!members.isEmpty()) {
            List<Long> taskIds = members.stream().map(Long::parseLong).toList();
            GpuTask oldest = gpuTaskMapper.selectOne(
                    new LambdaQueryWrapper<GpuTask>()
                            .in(GpuTask::getId, taskIds)
                            .isNotNull(GpuTask::getEnqueueAt)
                            .orderByAsc(GpuTask::getEnqueueAt)
                            .last("LIMIT 1"));
            if (oldest != null && oldest.getEnqueueAt() != null) {
                oldestWait = Duration.between(oldest.getEnqueueAt(), LocalDateTime.now()).toSeconds();
            }
        }

        String overallStatus;
        if ("DOWN".equals(dbStatus) || "DOWN".equals(redisStatus)) {
            overallStatus = "DOWN";
        } else if ("OPEN".equals(cbState)) {
            overallStatus = "DEGRADED";
        } else {
            overallStatus = "UP";
        }

        return SystemHealth.builder()
                .status(overallStatus)
                .dbStatus(dbStatus)
                .redisStatus(redisStatus)
                .schedulerStatus("UP")
                .circuitBreakerState(cbState)
                .oldestQueuedTaskSeconds(oldestWait)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String checkDb() {
        try {
            gpuMapper.selectCount(null);
            return "UP";
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            if (redisTemplate.getConnectionFactory() != null) {
                redisTemplate.getConnectionFactory().getConnection().ping();
            }
            return "UP";
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String priorityLabel(Integer basePriority) {
        if (basePriority == null) return "Unknown";
        if (basePriority >= 8) return "High(8-10)";
        if (basePriority >= 5) return "Medium(5-7)";
        return "Low(1-4)";
    }

    private double percentile(List<Long> values, double pct) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        int idx = (int) Math.ceil(pct * values.size()) - 1;
        idx = Math.max(0, Math.min(idx, values.size() - 1));
        return values.get(idx);
    }

    private Map<String, Long> initQueueAgeHistogram() {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("lt_1m", 0L);
        buckets.put("m1_5", 0L);
        buckets.put("m5_15", 0L);
        buckets.put("gte_15m", 0L);
        return buckets;
    }

    private String queueAgeBucket(long ageSeconds) {
        if (ageSeconds < 60) return "lt_1m";
        if (ageSeconds < 300) return "m1_5";
        if (ageSeconds < 900) return "m5_15";
        return "gte_15m";
    }

    private String classifyAllocationFailure(String message) {
        String lower = message.toLowerCase();
        if (!(lower.contains("gpu") || lower.contains("alloc") || lower.contains("memory"))) {
            return "NOT_ALLOCATION";
        }
        if (lower.contains("no available") || lower.contains("not available") || lower.contains("insufficient")) {
            return "NO_AVAILABLE_GPU";
        }
        if (lower.contains("memory") || lower.contains("vram")) {
            return "INSUFFICIENT_MEMORY";
        }
        if (lower.contains("offline") || lower.contains("maintenance")) {
            return "GPU_UNAVAILABLE";
        }
        return "OTHER";
    }

    private Map<Long, Double> calculateUserSlaCompliance(List<GpuTask> allTasks) {
        Map<Long, List<GpuTask>> byUser = allTasks.stream()
                .filter(t -> t.getUserId() != null)
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED.getCode())
                .filter(t -> t.getEstimatedSeconds() != null && t.getActualSeconds() != null)
                .collect(Collectors.groupingBy(GpuTask::getUserId));

        Map<Long, Double> result = new LinkedHashMap<>();
        byUser.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    List<GpuTask> tasks = new ArrayList<>(entry.getValue());
                    long onTime = tasks.stream()
                            .filter(t -> t.getActualSeconds()
                                    .compareTo(t.getEstimatedSeconds().multiply(new BigDecimal("1.10"))) <= 0)
                            .count();
                    double pct = tasks.isEmpty() ? 0.0 : onTime * 100.0 / tasks.size();
                    result.put(entry.getKey(), new BigDecimal(pct).setScale(1, RoundingMode.HALF_UP).doubleValue());
                });
        return result;
    }
}

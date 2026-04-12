package com.sakurain.gpuscheduler.service;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardInitialResponse;
import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardOverviewResponse;
import com.sakurain.gpuscheduler.dto.dashboard.DatabaseHealthMetrics;
import com.sakurain.gpuscheduler.dto.dashboard.DlqItemResponse;
import com.sakurain.gpuscheduler.dto.dashboard.DlqListResponse;
import com.sakurain.gpuscheduler.dto.dashboard.DlqSummary;
import com.sakurain.gpuscheduler.dto.dashboard.GpuMemoryBreakdown;
import com.sakurain.gpuscheduler.dto.dashboard.MemoryFragmentationResponse;
import com.sakurain.gpuscheduler.dto.dashboard.QueueWaitTrendPoint;
import com.sakurain.gpuscheduler.dto.dashboard.QueueWaitTrendResponse;
import com.sakurain.gpuscheduler.dto.dashboard.RedisHealthMetrics;
import com.sakurain.gpuscheduler.dto.dashboard.SchedulerThreadPoolMetrics;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.scheduler.CircuitBreakerService;
import com.sakurain.gpuscheduler.scheduler.TaskExecutionSimulator;
import com.sakurain.gpuscheduler.util.PaginationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminDashboardService {

    private static final String DLQ_KEY = "gpu:task:dlq";
    private static final String TREND_CACHE_KEY_PREFIX = "admin:dashboard:queue-trend:";
    private static final List<String> DAY_LABELS = List.of(
            "00:00", "01:00", "02:00", "03:00", "04:00", "05:00",
            "06:00", "07:00", "08:00", "09:00", "10:00", "11:00",
            "12:00", "13:00", "14:00", "15:00", "16:00", "17:00",
            "18:00", "19:00", "20:00", "21:00", "22:00", "23:00"
    );
    private static final List<String> WEEK_LABELS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private final GpuTaskMapper gpuTaskMapper;
    private final GpuMapper gpuMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final CircuitBreakerService circuitBreakerService;
    private final TaskExecutionSimulator taskExecutionSimulator;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Value("${admin-dashboard.trend-cache-ttl-seconds:300}")
    private long trendCacheTtlSeconds;

    private volatile long lastDbExecuteCount = -1L;
    private volatile long lastDbSampleAtMillis = -1L;
    private volatile double lastDbQps = 0.0D;

    public AdminDashboardService(GpuTaskMapper gpuTaskMapper,
                                 GpuMapper gpuMapper,
                                 UserMapper userMapper,
                                 RedisTemplate<String, String> redisTemplate,
                                 CircuitBreakerService circuitBreakerService,
                                 TaskExecutionSimulator taskExecutionSimulator,
                                 DataSource dataSource,
                                 ObjectMapper objectMapper) {
        this.gpuTaskMapper = gpuTaskMapper;
        this.gpuMapper = gpuMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.circuitBreakerService = circuitBreakerService;
        this.taskExecutionSimulator = taskExecutionSimulator;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public AdminDashboardInitialResponse getInitialSnapshot(LocalDateTime lastTelemetryAt) {
        return AdminDashboardInitialResponse.builder()
                .overview(buildOverview())
                .memoryFragmentation(buildMemoryFragmentation())
                .dlqSummary(buildDlqSummary())
                .lastTelemetryAt(lastTelemetryAt)
                .build();
    }

    public AdminDashboardOverviewResponse buildOverview() {
        return AdminDashboardOverviewResponse.builder()
                .mysql(buildDatabaseHealth())
                .redis(buildRedisHealth())
                .schedulerThreadPool(SchedulerThreadPoolMetrics.builder()
                        .activeThreads(taskExecutionSimulator.getActiveThreadCount())
                        .coreThreads(taskExecutionSimulator.getCoreThreadCount())
                        .queuedTasks(taskExecutionSimulator.getQueuedTaskCount())
                        .build())
                .circuitBreakerState(circuitBreakerService.getState().name())
                .build();
    }

    public MemoryFragmentationResponse buildMemoryFragmentation() {
        List<Gpu> gpus = gpuMapper.selectList(null);
        List<GpuTask> runningTasks = gpuTaskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
                .isNotNull(GpuTask::getGpuId));
        Map<Long, GpuTask> runningTaskByGpuId = runningTasks.stream()
                .collect(Collectors.toMap(GpuTask::getGpuId, task -> task, (left, right) -> left));

        BigDecimal totalUsed = BigDecimal.ZERO;
        BigDecimal totalFragmented = BigDecimal.ZERO;
        BigDecimal totalFree = BigDecimal.ZERO;
        List<GpuMemoryBreakdown> breakdowns = new ArrayList<>();

        for (Gpu gpu : gpus) {
            BigDecimal totalMemory = safe(gpu.getMemoryGb());
            GpuTask runningTask = runningTaskByGpuId.get(gpu.getId());
            BigDecimal used = runningTask == null ? BigDecimal.ZERO : safe(runningTask.getMinMemoryGb());
            BigDecimal remaining = totalMemory.subtract(used).max(BigDecimal.ZERO);
            BigDecimal fragmented = Objects.equals(gpu.getStatus(), GpuStatus.BUSY.getCode()) ? remaining : BigDecimal.ZERO;
            BigDecimal free = Objects.equals(gpu.getStatus(), GpuStatus.IDLE.getCode()) ? totalMemory : BigDecimal.ZERO;

            totalUsed = totalUsed.add(used);
            totalFragmented = totalFragmented.add(fragmented);
            totalFree = totalFree.add(free);

            breakdowns.add(GpuMemoryBreakdown.builder()
                    .gpuId(gpu.getId())
                    .gpuName(gpu.getName())
                    .status(GpuStatus.fromCode(gpu.getStatus()).getLabel())
                    .totalMemoryGb(scale(totalMemory))
                    .usedAllocatedMemoryGb(scale(used))
                    .fragmentedMemoryGb(scale(fragmented))
                    .freeMemoryGb(scale(free))
                    .build());
        }

        breakdowns.sort(Comparator.comparing(GpuMemoryBreakdown::getGpuId));
        return MemoryFragmentationResponse.builder()
                .usedAllocatedMemoryGb(scale(totalUsed))
                .fragmentedMemoryGb(scale(totalFragmented))
                .freeMemoryGb(scale(totalFree))
                .gpuBreakdowns(breakdowns)
                .build();
    }

    public DlqSummary buildDlqSummary() {
        Long size = redisTemplate.opsForList().size(DLQ_KEY);
        List<String> latest = redisTemplate.opsForList().range(DLQ_KEY, 0, 0);
        ParsedDlqPayload latestPayload = latest == null || latest.isEmpty()
                ? new ParsedDlqPayload(null, 0L, "", null)
                : parseDlqPayload(latest.get(0));

        return DlqSummary.builder()
                .size(size == null ? 0L : size)
                .latestEnteredDlqAt(latestPayload.enteredDlqAt())
                .build();
    }

    public DlqListResponse listDlq(Integer page, Integer size) {
        long current = PaginationUtils.normalizePage(page);
        long pageSize = PaginationUtils.normalizeSize(size, 20, 200);
        long start = (current - 1) * pageSize;
        long end = start + pageSize - 1;

        Long total = redisTemplate.opsForList().size(DLQ_KEY);
        List<String> rawItems = redisTemplate.opsForList().range(DLQ_KEY, start, end);
        List<ParsedDlqPayload> payloads = rawItems == null ? List.of() : rawItems.stream().map(this::parseDlqPayload).toList();
        Map<Long, GpuTask> taskById = loadTasks(payloads);
        Map<Long, User> userById = loadUsers(taskById.values());

        List<DlqItemResponse> records = payloads.stream()
                .map(payload -> {
                    GpuTask task = payload.taskId() == null ? null : taskById.get(payload.taskId());
                    User user = task == null || task.getUserId() == null ? null : userById.get(task.getUserId());
                    return DlqItemResponse.builder()
                            .taskId(payload.taskId())
                            .username(user != null ? user.getUsername() : null)
                            .email(user != null ? user.getEmail() : null)
                            .retryCount(payload.retryCount())
                            .failureReason(payload.failureReason())
                            .enteredDlqAt(payload.enteredDlqAt())
                            .build();
                })
                .toList();

        return DlqListResponse.builder()
                .current(current)
                .size(pageSize)
                .total(total == null ? 0L : total)
                .records(records)
                .build();
    }

    public QueueWaitTrendResponse getQueueWaitTrend(String mode, LocalDate anchorDate) {
        String normalizedMode = normalizeMode(mode);
        LocalDate normalizedDate = anchorDate != null ? anchorDate : LocalDate.now();
        String cacheKey = TREND_CACHE_KEY_PREFIX + normalizedMode + ":" + normalizedDate;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, QueueWaitTrendResponse.class);
            } catch (JsonProcessingException ex) {
                log.warn("Failed to read trend cache: {}", ex.getMessage());
            }
        }

        QueueWaitTrendResponse response = computeQueueWaitTrend(normalizedMode, normalizedDate);
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(Math.max(30, trendCacheTtlSeconds)));
        } catch (Exception ex) {
            log.warn("Failed to write trend cache: {}", ex.getMessage());
        }
        return response;
    }

    private QueueWaitTrendResponse computeQueueWaitTrend(String mode, LocalDate anchorDate) {
        TrendWindow window = buildTrendWindow(mode, anchorDate);
        List<GpuTask> sampleTasks = gpuTaskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .isNotNull(GpuTask::getEnqueueAt)
                .isNotNull(GpuTask::getDispatchedAt)
                .ge(GpuTask::getEnqueueAt, window.start())
                .lt(GpuTask::getEnqueueAt, window.end())
                .orderByAsc(GpuTask::getEnqueueAt)
                .orderByAsc(GpuTask::getId));
        if (sampleTasks.isEmpty()) {
            return QueueWaitTrendResponse.builder()
                    .mode(mode)
                    .anchorDate(anchorDate)
                    .bucketUnit(window.bucketUnit())
                    .points(buildEmptyPoints(window))
                    .build();
        }

        Map<Integer, BucketAccumulator> actualBuckets = initBuckets(window.bucketCount());
        Map<Integer, BucketAccumulator> simulatedBuckets = initBuckets(window.bucketCount());

        for (GpuTask task : sampleTasks) {
            int bucketIndex = resolveBucketIndex(task.getEnqueueAt(), window);
            if (bucketIndex >= 0) {
                actualBuckets.get(bucketIndex).add(secondsBetween(task.getEnqueueAt(), task.getDispatchedAt()));
            }
        }

        List<Gpu> gpus = gpuMapper.selectList(null);
        Map<Long, LocalDateTime> availabilityByGpuId = buildInitialAvailability(window.start());
        for (SimulatedAssignment assignment : simulateFifoAssignments(sampleTasks, gpus, availabilityByGpuId)) {
            int bucketIndex = resolveBucketIndex(assignment.enqueueAt(), window);
            if (bucketIndex >= 0) {
                simulatedBuckets.get(bucketIndex).add(secondsBetween(assignment.enqueueAt(), assignment.dispatchedAt()));
            }
        }

        List<QueueWaitTrendPoint> points = new ArrayList<>(window.bucketCount());
        for (int i = 0; i < window.bucketCount(); i++) {
            points.add(QueueWaitTrendPoint.builder()
                    .bucketStart(window.start().plus(window.bucketDuration().multipliedBy(i)))
                    .label(window.labels().get(i))
                    .actualAgingAvgWaitSeconds(actualBuckets.get(i).average())
                    .simulatedFifoAvgWaitSeconds(simulatedBuckets.get(i).average())
                    .build());
        }
        return QueueWaitTrendResponse.builder()
                .mode(mode)
                .anchorDate(anchorDate)
                .bucketUnit(window.bucketUnit())
                .points(points)
                .build();
    }

    private List<QueueWaitTrendPoint> buildEmptyPoints(TrendWindow window) {
        List<QueueWaitTrendPoint> points = new ArrayList<>(window.bucketCount());
        for (int i = 0; i < window.bucketCount(); i++) {
            points.add(QueueWaitTrendPoint.builder()
                    .bucketStart(window.start().plus(window.bucketDuration().multipliedBy(i)))
                    .label(window.labels().get(i))
                    .actualAgingAvgWaitSeconds(0.0)
                    .simulatedFifoAvgWaitSeconds(0.0)
                    .build());
        }
        return points;
    }

    private List<SimulatedAssignment> simulateFifoAssignments(List<GpuTask> tasks,
                                                              List<Gpu> gpus,
                                                              Map<Long, LocalDateTime> availabilityByGpuId) {
        List<SimulatedAssignment> assignments = new ArrayList<>();
        for (GpuTask task : tasks) {
            if (task.getEnqueueAt() == null) {
                continue;
            }
            List<Gpu> eligibleGpus = gpus.stream()
                    .filter(gpu -> gpu.getMemoryGb() != null)
                    .filter(gpu -> gpu.getMemoryGb().compareTo(safe(task.getMinMemoryGb())) >= 0)
                    .sorted(Comparator.comparing(Gpu::getMemoryGb).thenComparing(Gpu::getId))
                    .toList();
            if (eligibleGpus.isEmpty()) {
                continue;
            }

            LocalDateTime current = task.getEnqueueAt();
            Gpu selectedGpu;
            while (true) {
                LocalDateTime threshold = current;
                selectedGpu = eligibleGpus.stream()
                        .filter(gpu -> !availabilityByGpuId.getOrDefault(gpu.getId(), threshold).isAfter(threshold))
                        .findFirst()
                        .orElse(null);
                if (selectedGpu != null) {
                    break;
                }
                current = eligibleGpus.stream()
                        .map(gpu -> availabilityByGpuId.getOrDefault(gpu.getId(), threshold))
                        .min(LocalDateTime::compareTo)
                        .orElse(current);
            }

            LocalDateTime dispatchAt = current;
            availabilityByGpuId.put(selectedGpu.getId(), dispatchAt.plus(resolveRuntime(task)));
            assignments.add(new SimulatedAssignment(task.getEnqueueAt(), dispatchAt));
        }
        return assignments;
    }

    private Map<Long, LocalDateTime> buildInitialAvailability(LocalDateTime start) {
        List<GpuTask> blockers = gpuTaskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .isNotNull(GpuTask::getGpuId)
                .isNotNull(GpuTask::getDispatchedAt)
                .lt(GpuTask::getDispatchedAt, start));
        Map<Long, LocalDateTime> availability = new HashMap<>();
        for (GpuTask blocker : blockers) {
            LocalDateTime releaseAt = resolveReleaseAt(blocker);
            if (blocker.getGpuId() != null && releaseAt != null && releaseAt.isAfter(start)) {
                availability.merge(blocker.getGpuId(), releaseAt,
                        (left, right) -> left.isAfter(right) ? left : right);
            }
        }
        return availability;
    }

    private TrendWindow buildTrendWindow(String mode, LocalDate anchorDate) {
        if ("WEEK".equals(mode)) {
            LocalDate weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            return new TrendWindow(weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay(),
                    7, Duration.ofDays(1), WEEK_LABELS, "DAY");
        }
        return new TrendWindow(anchorDate.atStartOfDay(), anchorDate.plusDays(1).atStartOfDay(),
                24, Duration.ofHours(1), DAY_LABELS, "HOUR");
    }

    private int resolveBucketIndex(LocalDateTime enqueueAt, TrendWindow window) {
        if (enqueueAt == null || enqueueAt.isBefore(window.start()) || !enqueueAt.isBefore(window.end())) {
            return -1;
        }
        long deltaSeconds = Duration.between(window.start(), enqueueAt).getSeconds();
        long bucketSeconds = Math.max(1L, window.bucketDuration().getSeconds());
        return (int) Math.min(window.bucketCount() - 1, deltaSeconds / bucketSeconds);
    }

    private Map<Integer, BucketAccumulator> initBuckets(int count) {
        Map<Integer, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            buckets.put(i, new BucketAccumulator());
        }
        return buckets;
    }

    private DatabaseHealthMetrics buildDatabaseHealth() {
        try {
            gpuMapper.selectCount(null);
            return DatabaseHealthMetrics.builder()
                    .status("UP")
                    .qps(readDatabaseQps())
                    .build();
        } catch (Exception ex) {
            log.warn("Database health probe failed: {}", ex.getMessage());
            return DatabaseHealthMetrics.builder().status("DOWN").qps(0.0).build();
        }
    }

    private RedisHealthMetrics buildRedisHealth() {
        try {
            return RedisHealthMetrics.builder()
                    .status("UP")
                    .fragmentationRatio(readRedisFragmentationRatio())
                    .build();
        } catch (Exception ex) {
            log.warn("Redis health probe failed: {}", ex.getMessage());
            return RedisHealthMetrics.builder().status("DOWN").fragmentationRatio(0.0).build();
        }
    }

    private double readDatabaseQps() {
        long executeCount = extractExecuteCount(dataSource);
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (lastDbExecuteCount < 0 || lastDbSampleAtMillis < 0) {
                lastDbExecuteCount = executeCount;
                lastDbSampleAtMillis = now;
                lastDbQps = 0.0D;
                return lastDbQps;
            }
            long deltaCount = Math.max(0L, executeCount - lastDbExecuteCount);
            long deltaMillis = Math.max(1L, now - lastDbSampleAtMillis);
            lastDbExecuteCount = executeCount;
            lastDbSampleAtMillis = now;
            lastDbQps = round(deltaCount * 1000.0D / deltaMillis);
            return lastDbQps;
        }
    }

    private long extractExecuteCount(DataSource source) {
        if (source == null) {
            return 0L;
        }
        if (source instanceof DruidDataSource druidDataSource) {
            Object direct = invokeNoArg(druidDataSource, "getExecuteCount");
            if (direct instanceof Number number) {
                return number.longValue();
            }
            Object stat = invokeNoArg(druidDataSource, "getDataSourceStat");
            if (stat != null) {
                Object nested = invokeNoArg(stat, "getExecuteCount");
                if (nested instanceof Number nestedNumber) {
                    return nestedNumber.longValue();
                }
            }
        }
        Object reflected = invokeNoArg(source, "getExecuteCount");
        return reflected instanceof Number number ? number.longValue() : 0L;
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    private double readRedisFragmentationRatio() {
        if (redisTemplate.getConnectionFactory() == null) {
            return 0.0;
        }
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        try {
            connection.ping();
            Properties info = connection.info("memory");
            String raw = info == null ? null : info.getProperty("mem_fragmentation_ratio");
            if (raw == null || raw.isBlank()) {
                return 0.0;
            }
            return round(Double.parseDouble(raw));
        } finally {
            connection.close();
        }
    }

    private Map<Long, GpuTask> loadTasks(List<ParsedDlqPayload> payloads) {
        List<Long> taskIds = payloads.stream()
                .map(ParsedDlqPayload::taskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return gpuTaskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(GpuTask::getId, task -> task));
    }

    private Map<Long, User> loadUsers(Collection<GpuTask> tasks) {
        List<Long> userIds = tasks.stream()
                .map(GpuTask::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private ParsedDlqPayload parseDlqPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ParsedDlqPayload(null, 0L, "", null);
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            Long taskId = root.has("taskId") && root.get("taskId").canConvertToLong()
                    ? root.get("taskId").longValue() : null;
            long attempt = root.has("attempt") && root.get("attempt").canConvertToLong()
                    ? root.get("attempt").longValue() : 0L;
            String reason = root.has("reason") ? root.get("reason").asText("") : "";
            LocalDateTime time = null;
            if (root.has("time") && !root.get("time").isNull()) {
                String raw = root.get("time").asText();
                if (!raw.isBlank()) {
                    time = LocalDateTime.parse(raw);
                }
            }
            return new ParsedDlqPayload(taskId, attempt, reason, time);
        } catch (Exception ex) {
            log.warn("Failed to parse DLQ payload: {}", ex.getMessage());
            return new ParsedDlqPayload(null, 0L, payload, null);
        }
    }

    private LocalDateTime resolveReleaseAt(GpuTask task) {
        if (task.getFinishedAt() != null) {
            return task.getFinishedAt();
        }
        if (task.getEstimatedFinishAt() != null) {
            return task.getEstimatedFinishAt();
        }
        if (task.getDispatchedAt() != null) {
            return task.getDispatchedAt().plus(resolveRuntime(task));
        }
        return null;
    }

    private Duration resolveRuntime(GpuTask task) {
        BigDecimal seconds = task.getActualSeconds();
        if (seconds == null || seconds.compareTo(BigDecimal.ZERO) <= 0) {
            seconds = task.getEstimatedSeconds();
        }
        if (seconds == null || seconds.compareTo(BigDecimal.ZERO) <= 0) {
            if (task.getDispatchedAt() != null && task.getFinishedAt() != null) {
                return Duration.between(task.getDispatchedAt(), task.getFinishedAt());
            }
            return Duration.ZERO;
        }
        return Duration.ofMillis(seconds.multiply(new BigDecimal("1000")).longValue());
    }

    private double secondsBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0.0;
        }
        return round(Duration.between(start, end).toMillis() / 1000.0D);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String normalizeMode(String mode) {
        return "WEEK".equalsIgnoreCase(mode) ? "WEEK" : "DAY";
    }

    private record ParsedDlqPayload(Long taskId, long retryCount, String failureReason, LocalDateTime enteredDlqAt) {
    }

    private static final class BucketAccumulator {
        private final List<Double> values = new ArrayList<>();

        private void add(double value) {
            values.add(value);
        }

        private double average() {
            if (values.isEmpty()) {
                return 0.0;
            }
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            return BigDecimal.valueOf(sum / values.size()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }

    private record TrendWindow(LocalDateTime start,
                               LocalDateTime end,
                               int bucketCount,
                               Duration bucketDuration,
                               List<String> labels,
                               String bucketUnit) {
    }

    private record SimulatedAssignment(LocalDateTime enqueueAt, LocalDateTime dispatchedAt) {
    }
}

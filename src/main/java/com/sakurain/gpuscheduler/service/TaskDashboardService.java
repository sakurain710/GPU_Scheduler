package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.dto.task.CurrentUserTaskStats;
import com.sakurain.gpuscheduler.dto.task.GpuRadarSnapshot;
import com.sakurain.gpuscheduler.dto.task.PriorityQueueTopItem;
import com.sakurain.gpuscheduler.dto.task.TaskDashboardResponse;
import com.sakurain.gpuscheduler.dto.task.TaskWorkbenchItem;
import com.sakurain.gpuscheduler.dto.task.TaskWorkbenchPage;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.util.PaginationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskDashboardService {

    private static final String COMPLETED_RESULT_SUMMARY = "任务已归档，可查看运行日志和资源分配记录。";

    private final GpuTaskMapper taskMapper;
    private final GpuMapper gpuMapper;
    private final TaskPriorityQueue priorityQueue;
    private final TaskAgingScheduler agingScheduler;

    public TaskDashboardService(GpuTaskMapper taskMapper,
                                GpuMapper gpuMapper,
                                TaskPriorityQueue priorityQueue,
                                TaskAgingScheduler agingScheduler) {
        this.taskMapper = taskMapper;
        this.gpuMapper = gpuMapper;
        this.priorityQueue = priorityQueue;
        this.agingScheduler = agingScheduler;
    }

    public TaskDashboardResponse getDashboard(Long userId,
                                              Integer page,
                                              Integer size,
                                              Integer status,
                                              String sortBy,
                                              String sortDir) {
        return TaskDashboardResponse.builder()
                .userStats(getCurrentUserTaskStats(userId))
                .taskList(getDashboardTaskList(userId, page, size, status, sortBy, sortDir))
                .gpuRadar(getGpuRadarSnapshot())
                .priorityTop5(getPriorityTop5(userId))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public TaskWorkbenchPage getDashboardTaskList(Long userId,
                                                  Integer page,
                                                  Integer size,
                                                  Integer status,
                                                  String sortBy,
                                                  String sortDir) {
        Page<GpuTask> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 10, 200)
        );
        LambdaQueryWrapper<GpuTask> wrapper = new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getUserId, userId);
        if (status != null) {
            wrapper.eq(GpuTask::getStatus, status);
        }
        applyTaskSort(wrapper, sortBy, sortDir);

        IPage<GpuTask> taskPage = taskMapper.selectPage(pageParam, wrapper);
        Map<Long, Gpu> gpuById = loadGpuMap(taskPage.getRecords());
        List<TaskWorkbenchItem> records = taskPage.getRecords().stream()
                .map(task -> toWorkbenchItem(task, gpuById, LocalDateTime.now()))
                .toList();

        return TaskWorkbenchPage.builder()
                .records(records)
                .total(taskPage.getTotal())
                .size(taskPage.getSize())
                .current(taskPage.getCurrent())
                .pages(taskPage.getPages())
                .build();
    }

    public CurrentUserTaskStats getCurrentUserTaskStats(Long userId) {
        List<GpuTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getUserId, userId));
        Map<Long, Gpu> gpuById = loadGpuMap(tasks);

        List<BigDecimal> runningFitScores = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.RUNNING.getCode())
                .map(task -> calculateMemoryFitScore(task, gpuById))
                .filter(Objects::nonNull)
                .toList();

        List<BigDecimal> queuedAgingScores = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.QUEUED.getCode())
                .map(this::calculateAgingScore)
                .toList();

        LocalDateTime startOfWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime endOfWeek = LocalDate.now()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atTime(LocalTime.MAX);

        long weeklyCompleted = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED.getCode())
                .filter(task -> isBetween(task.getFinishedAt(), startOfWeek, endOfWeek))
                .count();
        long weeklyTerminal = tasks.stream()
                .filter(task -> TaskStatus.fromCode(task.getStatus()).isTerminal())
                .filter(task -> isBetween(resolveTerminalTime(task), startOfWeek, endOfWeek))
                .count();

        return CurrentUserTaskStats.builder()
                .runningTaskCount(countByStatus(tasks, TaskStatus.RUNNING))
                .avgRunningMemoryFitScore(average(runningFitScores))
                .queuedTaskCount(countByStatus(tasks, TaskStatus.QUEUED))
                .maxQueuedAgingScore(max(queuedAgingScores))
                .completedTaskCount(countByStatus(tasks, TaskStatus.COMPLETED))
                .weeklySuccessRate(rate(weeklyCompleted, weeklyTerminal))
                .build();
    }

    public GpuRadarSnapshot getGpuRadarSnapshot() {
        List<Gpu> allGpus = gpuMapper.selectList(null);
        long totalGpuCount = allGpus.size();
        long availableGpuCount = allGpus.stream()
                .filter(gpu -> Objects.equals(gpu.getStatus(), GpuStatus.IDLE.getCode()))
                .count();
        BigDecimal totalMemory = getTotalMemoryGb();
        BigDecimal freeMemory = getSystemFreeMemoryGb();
        BigDecimal overallUtilizationRate = totalMemory.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalMemory.subtract(freeMemory).max(BigDecimal.ZERO)
                .multiply(new BigDecimal("100"))
                .divide(totalMemory, 2, RoundingMode.HALF_UP);
        LocalDateTime nextReleaseAt = getNextReleaseAt();
        Long nextReleaseInSeconds = nextReleaseAt == null
                ? null
                : Math.max(0L, Duration.between(LocalDateTime.now(), nextReleaseAt).toSeconds());

        return GpuRadarSnapshot.builder()
                .totalGpuCount(totalGpuCount)
                .availableGpuCount(availableGpuCount)
                .overallUtilizationRate(overallUtilizationRate)
                .systemAvgWaitSeconds(getSystemAvgQueuedWaitSeconds())
                .systemFreeMemoryGb(freeMemory)
                .nextReleaseAt(nextReleaseAt)
                .nextReleaseInSeconds(nextReleaseInSeconds)
                .build();
    }

    public List<PriorityQueueTopItem> getPriorityTop5(Long currentUserId) {
        List<Long> topIds = priorityQueue.topMembers(5);
        if (topIds.isEmpty()) {
            return List.of();
        }
        List<GpuTask> tasks = taskMapper.selectBatchIds(topIds);
        Map<Long, GpuTask> taskById = tasks.stream()
                .collect(Collectors.toMap(GpuTask::getId, task -> task));

        Map<Long, String> anonymousNameByUser = new LinkedHashMap<>();
        List<PriorityQueueTopItem> items = new ArrayList<>();
        for (Long taskId : topIds) {
            GpuTask task = taskById.get(taskId);
            if (task == null) {
                continue;
            }
            boolean isCurrentUser = Objects.equals(task.getUserId(), currentUserId);
            items.add(PriorityQueueTopItem.builder()
                    .taskId(task.getId())
                    .displayName(resolveDisplayName(task, currentUserId, anonymousNameByUser))
                    .minMemoryGb(task.getMinMemoryGb())
                    .agingScore(calculateAgingScore(task))
                    .isCurrentUser(isCurrentUser)
                    .build());
        }
        return items;
    }

    public TaskWorkbenchItem toWorkbenchItem(GpuTask task,
                                             Map<Long, Gpu> gpuById,
                                             LocalDateTime now) {
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        Long runningSeconds = calculateRunningSeconds(task, status, now);
        Long totalDurationSeconds = calculateTotalDurationSeconds(task, status);
        Long waitSeconds = calculateWaitSeconds(task, status, now);
        Long queuePosition = status == TaskStatus.QUEUED ? resolveQueuePosition(task.getId()) : null;
        BigDecimal agingScore = status == TaskStatus.QUEUED ? calculateAgingScore(task) : null;
        BigDecimal memoryFitScore = status == TaskStatus.RUNNING ? calculateMemoryFitScore(task, gpuById) : null;
        BigDecimal progressPct = status == TaskStatus.RUNNING ? calculateProgressPct(task, runningSeconds) : null;
        Long remainingExecutionSeconds = status == TaskStatus.RUNNING
                ? calculateRemainingExecutionSeconds(task, runningSeconds)
                : null;

        return TaskWorkbenchItem.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .gpuId(task.getGpuId())
                .title(task.getTitle())
                .description(task.getDescription())
                .taskType(task.getTaskType())
                .minMemoryGb(task.getMinMemoryGb())
                .status(task.getStatus())
                .statusLabel(status.getLabel())
                .basePriority(task.getBasePriority())
                .estimatedSeconds(task.getEstimatedSeconds())
                .actualSeconds(task.getActualSeconds())
                .errorMessage(task.getErrorMessage())
                .enqueueAt(task.getEnqueueAt())
                .dispatchedAt(task.getDispatchedAt())
                .estimatedFinishAt(task.getEstimatedFinishAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .queuePosition(queuePosition)
                .runningSeconds(runningSeconds)
                .totalDurationSeconds(totalDurationSeconds)
                .reviewStatus(resolveReviewStatus(status))
                .waitSeconds(waitSeconds)
                .resultSummary(resolveResultSummary(task, status))
                .agingScore(agingScore)
                .progressPct(progressPct)
                .remainingExecutionSeconds(remainingExecutionSeconds)
                .memoryFitScore(memoryFitScore)
                .build();
    }

    public void pushDashboardSnapshotToUser(org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
                                            String topicPrefix,
                                            Long userId) {
        if (userId == null) {
            return;
        }
        messagingTemplate.convertAndSend(topicPrefix + userId, getDashboard(userId, 1, 10, null, "updatedAt", "desc"));
    }

    private void applyTaskSort(LambdaQueryWrapper<GpuTask> wrapper,
                               String sortBy,
                               String sortDir) {
        boolean asc = sortDir != null && "asc".equalsIgnoreCase(sortDir);
        String key = sortBy == null ? "updatedAt" : sortBy;
        switch (key) {
            case "basePriority" -> wrapper.orderBy(true, asc, GpuTask::getBasePriority);
            case "enqueueAt" -> wrapper.orderBy(true, asc, GpuTask::getEnqueueAt);
            case "status" -> wrapper.orderBy(true, asc, GpuTask::getStatus);
            case "id" -> wrapper.orderBy(true, asc, GpuTask::getId);
            case "createdAt" -> wrapper.orderBy(true, asc, GpuTask::getCreatedAt);
            default -> wrapper.orderBy(true, asc, GpuTask::getUpdatedAt);
        }
    }

    private BigDecimal getTotalMemoryGb() {
        return gpuMapper.selectList(null).stream()
                .map(Gpu::getMemoryGb)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getSystemFreeMemoryGb() {
        List<Gpu> allGpus = gpuMapper.selectList(null);
        if (allGpus.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Map<Long, GpuTask> runningTaskByGpuId = taskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                        .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
                        .isNotNull(GpuTask::getGpuId))
                .stream()
                .collect(Collectors.toMap(GpuTask::getGpuId, task -> task, (left, right) -> left));
        BigDecimal freeMemory = BigDecimal.ZERO;
        for (Gpu gpu : allGpus) {
            BigDecimal gpuMemory = gpu.getMemoryGb() != null ? gpu.getMemoryGb() : BigDecimal.ZERO;
            GpuTask runningTask = runningTaskByGpuId.get(gpu.getId());
            BigDecimal usedMemory = runningTask != null && runningTask.getMinMemoryGb() != null
                    ? runningTask.getMinMemoryGb()
                    : BigDecimal.ZERO;
            freeMemory = freeMemory.add(gpuMemory.subtract(usedMemory).max(BigDecimal.ZERO));
        }
        return freeMemory.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getSystemAvgQueuedWaitSeconds() {
        List<GpuTask> queuedTasks = taskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getStatus, TaskStatus.QUEUED.getCode())
                .isNotNull(GpuTask::getEnqueueAt));
        if (queuedTasks.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        double avgSeconds = queuedTasks.stream()
                .mapToLong(task -> Duration.between(task.getEnqueueAt(), LocalDateTime.now()).toSeconds())
                .average()
                .orElse(0.0);
        return BigDecimal.valueOf(avgSeconds).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime getNextReleaseAt() {
        GpuTask nextTask = taskMapper.selectOne(new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getStatus, TaskStatus.RUNNING.getCode())
                .isNotNull(GpuTask::getEstimatedFinishAt)
                .orderByAsc(GpuTask::getEstimatedFinishAt)
                .last("LIMIT 1"));
        return nextTask != null ? nextTask.getEstimatedFinishAt() : null;
    }

    private Map<Long, Gpu> loadGpuMap(List<GpuTask> tasks) {
        List<Long> gpuIds = tasks.stream()
                .map(GpuTask::getGpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (gpuIds.isEmpty()) {
            return Map.of();
        }
        return gpuMapper.selectBatchIds(gpuIds).stream()
                .collect(Collectors.toMap(Gpu::getId, gpu -> gpu));
    }

    private Long countByStatus(List<GpuTask> tasks, TaskStatus targetStatus) {
        return tasks.stream()
                .filter(task -> task.getStatus() == targetStatus.getCode())
                .count();
    }

    private BigDecimal calculateMemoryFitScore(GpuTask task, Map<Long, Gpu> gpuById) {
        if (task.getGpuId() == null || task.getMinMemoryGb() == null) {
            return null;
        }
        Gpu gpu = gpuById.get(task.getGpuId());
        if (gpu == null || gpu.getMemoryGb() == null || gpu.getMemoryGb().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return task.getMinMemoryGb().divide(gpu.getMemoryGb(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAgingScore(GpuTask task) {
        return BigDecimal.valueOf(agingScheduler.calculateEffectivePriority(task))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Long calculateRunningSeconds(GpuTask task, TaskStatus status, LocalDateTime now) {
        if (status != TaskStatus.RUNNING || task.getDispatchedAt() == null) {
            return null;
        }
        return Math.max(0L, Duration.between(task.getDispatchedAt(), now).toSeconds());
    }

    private Long calculateTotalDurationSeconds(GpuTask task, TaskStatus status) {
        if (!status.isTerminal()) {
            return null;
        }
        if (task.getEnqueueAt() != null && task.getFinishedAt() != null) {
            return Math.max(0L, Duration.between(task.getEnqueueAt(), task.getFinishedAt()).toSeconds());
        }
        return task.getActualSeconds() != null ? task.getActualSeconds().longValue() : null;
    }

    private Long calculateWaitSeconds(GpuTask task, TaskStatus status, LocalDateTime now) {
        if (status != TaskStatus.QUEUED || task.getEnqueueAt() == null) {
            return null;
        }
        return Math.max(0L, Duration.between(task.getEnqueueAt(), now).toSeconds());
    }

    private Long resolveQueuePosition(Long taskId) {
        Long rank = priorityQueue.rank(taskId);
        return rank == null ? null : rank + 1;
    }

    private BigDecimal calculateProgressPct(GpuTask task, Long runningSeconds) {
        if (task.getEstimatedSeconds() == null || runningSeconds == null
                || task.getEstimatedSeconds().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal progress = BigDecimal.valueOf(runningSeconds)
                .multiply(new BigDecimal("100"))
                .divide(task.getEstimatedSeconds(), 2, RoundingMode.HALF_UP);
        if (progress.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100.00");
        }
        if (progress.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return progress;
    }

    private Long calculateRemainingExecutionSeconds(GpuTask task, Long runningSeconds) {
        if (task.getEstimatedSeconds() == null || runningSeconds == null) {
            return null;
        }
        return Math.max(0L, task.getEstimatedSeconds().longValue() - runningSeconds);
    }

    private String resolveReviewStatus(TaskStatus status) {
        if (status == TaskStatus.PENDING_APPROVAL) {
            return "待审核";
        }
        if (status == TaskStatus.REJECTED) {
            return "已拒绝";
        }
        return null;
    }

    private String resolveResultSummary(GpuTask task, TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return COMPLETED_RESULT_SUMMARY;
        }
        if (status == TaskStatus.FAILED || status == TaskStatus.REJECTED || status == TaskStatus.CANCELLED) {
            return task.getErrorMessage() != null && !task.getErrorMessage().isBlank()
                    ? summarize(task.getErrorMessage())
                    : status.getLabel();
        }
        return null;
    }

    private String summarize(String message) {
        return message.length() <= 100 ? message : message.substring(0, 100);
    }

    private LocalDateTime resolveTerminalTime(GpuTask task) {
        return task.getFinishedAt() != null ? task.getFinishedAt() : task.getUpdatedAt();
    }

    private boolean isBetween(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        return time != null && !time.isBefore(start) && !time.isAfter(end);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal max(List<BigDecimal> values) {
        return values.stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String resolveDisplayName(GpuTask task,
                                      Long currentUserId,
                                      Map<Long, String> anonymousNameByUser) {
        if (Objects.equals(task.getUserId(), currentUserId)) {
            return "我的任务 " + task.getTitle();
        }
        Long userId = task.getUserId();
        if (userId == null) {
            return "User_? " + task.getTitle();
        }
        String alias = anonymousNameByUser.computeIfAbsent(userId,
                key -> "User_" + (char) ('A' + anonymousNameByUser.size()));
        return alias + " " + task.getTitle();
    }
}

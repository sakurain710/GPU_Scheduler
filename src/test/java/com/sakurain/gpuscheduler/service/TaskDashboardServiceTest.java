package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.dto.task.CurrentUserTaskStats;
import com.sakurain.gpuscheduler.dto.task.GpuRadarSnapshot;
import com.sakurain.gpuscheduler.dto.task.PriorityQueueTopItem;
import com.sakurain.gpuscheduler.dto.task.TaskWorkbenchItem;
import com.sakurain.gpuscheduler.dto.task.TaskWorkbenchPage;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskAgingScheduler;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDashboardServiceTest {

    @Mock private GpuTaskMapper taskMapper;
    @Mock private GpuMapper gpuMapper;
    @Mock private TaskPriorityQueue priorityQueue;
    @Mock private TaskAgingScheduler agingScheduler;

    private TaskDashboardService taskDashboardService;

    private Gpu gpu80;

    @BeforeEach
    void setUp() {
        taskDashboardService = new TaskDashboardService(
                taskMapper,
                gpuMapper,
                priorityQueue,
                agingScheduler
        );
        gpu80 = Gpu.builder().id(9L).memoryGb(new BigDecimal("80")).build();
        lenient().when(gpuMapper.selectBatchIds(any())).thenReturn(List.of(gpu80));
        lenient().when(agingScheduler.calculateEffectivePriority(any(GpuTask.class)))
                .thenAnswer(invocation -> {
                    GpuTask task = invocation.getArgument(0);
                    return task.getBasePriority() + 1.5D;
                });
    }

    @Test
    void getCurrentUserTaskStats_shouldUseRunningFitQueuedAgingAndWeeklyRate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisWeek = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay()
                .plusDays(1);
        GpuTask running = GpuTask.builder()
                .id(1L).userId(1L).gpuId(9L).status(TaskStatus.RUNNING.getCode())
                .minMemoryGb(new BigDecimal("20")).basePriority(5)
                .dispatchedAt(now.minusSeconds(30)).build();
        GpuTask queued = GpuTask.builder()
                .id(2L).userId(1L).status(TaskStatus.QUEUED.getCode())
                .basePriority(6).enqueueAt(now.minusMinutes(5)).build();
        GpuTask completed = GpuTask.builder()
                .id(3L).userId(1L).status(TaskStatus.COMPLETED.getCode())
                .finishedAt(thisWeek).build();
        GpuTask failed = GpuTask.builder()
                .id(4L).userId(1L).status(TaskStatus.FAILED.getCode())
                .finishedAt(thisWeek.plusHours(1)).errorMessage("oom").build();

        when(taskMapper.selectList(any())).thenReturn(List.of(running, queued, completed, failed));

        CurrentUserTaskStats stats = taskDashboardService.getCurrentUserTaskStats(1L);

        assertThat(stats.getRunningTaskCount()).isEqualTo(1L);
        assertThat(stats.getAvgRunningMemoryFitScore()).isEqualByComparingTo("0.25");
        assertThat(stats.getQueuedTaskCount()).isEqualTo(1L);
        assertThat(stats.getMaxQueuedAgingScore()).isEqualByComparingTo("7.50");
        assertThat(stats.getCompletedTaskCount()).isEqualTo(1L);
        assertThat(stats.getWeeklySuccessRate()).isEqualByComparingTo("50.00");
    }

    @Test
    void getDashboardTaskList_shouldFillDerivedFieldsForStatuses() {
        LocalDateTime now = LocalDateTime.now();
        GpuTask queued = GpuTask.builder()
                .id(2L).userId(1L).status(TaskStatus.QUEUED.getCode())
                .basePriority(4).enqueueAt(now.minusSeconds(40))
                .title("queued").build();
        GpuTask running = GpuTask.builder()
                .id(3L).userId(1L).gpuId(9L).status(TaskStatus.RUNNING.getCode())
                .basePriority(7).title("running")
                .minMemoryGb(new BigDecimal("20"))
                .estimatedSeconds(new BigDecimal("100"))
                .dispatchedAt(now.minusSeconds(25))
                .estimatedFinishAt(now.plusSeconds(75))
                .build();
        GpuTask completed = GpuTask.builder()
                .id(4L).userId(1L).status(TaskStatus.COMPLETED.getCode())
                .title("completed")
                .enqueueAt(now.minusSeconds(300))
                .finishedAt(now.minusSeconds(50))
                .build();
        GpuTask pendingApproval = GpuTask.builder()
                .id(5L).userId(1L).status(TaskStatus.PENDING_APPROVAL.getCode())
                .title("approval")
                .build();
        Page<GpuTask> page = new Page<>(1, 10);
        page.setRecords(List.of(queued, running, completed, pendingApproval));
        page.setTotal(4);

        when(taskMapper.selectPage(any(), any())).thenReturn(page);
        when(priorityQueue.rank(2L)).thenReturn(0L);

        TaskWorkbenchPage result = taskDashboardService.getDashboardTaskList(1L, 1, 10, null, "updatedAt", "desc");

        assertThat(result.getTotal()).isEqualTo(4L);
        TaskWorkbenchItem queuedItem = result.getRecords().get(0);
        TaskWorkbenchItem runningItem = result.getRecords().get(1);
        TaskWorkbenchItem completedItem = result.getRecords().get(2);
        TaskWorkbenchItem approvalItem = result.getRecords().get(3);

        assertThat(queuedItem.getQueuePosition()).isEqualTo(1L);
        assertThat(queuedItem.getWaitSeconds()).isGreaterThanOrEqualTo(39L);
        assertThat(queuedItem.getAgingScore()).isEqualByComparingTo("5.50");

        assertThat(runningItem.getRunningSeconds()).isGreaterThanOrEqualTo(24L);
        assertThat(runningItem.getProgressPct()).isNotNull();
        assertThat(runningItem.getRemainingExecutionSeconds()).isBetween(74L, 76L);
        assertThat(runningItem.getMemoryFitScore()).isEqualByComparingTo("0.2500");

        assertThat(completedItem.getResultSummary()).isEqualTo("任务已归档，可查看运行日志和资源分配记录。");
        assertThat(completedItem.getTotalDurationSeconds()).isGreaterThanOrEqualTo(249L);

        assertThat(approvalItem.getReviewStatus()).isEqualTo("待审核");
    }

    @Test
    void getGpuRadarSnapshot_shouldUseMonitoringMetrics() {
        LocalDateTime nextRelease = LocalDateTime.now().plusSeconds(60);
        Gpu gpu1 = Gpu.builder().id(1L).memoryGb(new BigDecimal("64")).build();
        Gpu gpu2 = Gpu.builder().id(2L).memoryGb(new BigDecimal("64")).build();
        GpuTask runningTask = GpuTask.builder()
                .id(21L).gpuId(1L).status(TaskStatus.RUNNING.getCode())
                .minMemoryGb(new BigDecimal("32"))
                .estimatedFinishAt(nextRelease)
                .build();
        GpuTask queuedTask = GpuTask.builder()
                .id(22L).status(TaskStatus.QUEUED.getCode())
                .enqueueAt(LocalDateTime.now().minusSeconds(18))
                .build();
        when(gpuMapper.selectList(null)).thenReturn(List.of(gpu1, gpu2));
        when(taskMapper.selectList(any())).thenReturn(List.of(runningTask), List.of(queuedTask));
        when(taskMapper.selectOne(any())).thenReturn(runningTask);

        GpuRadarSnapshot snapshot = taskDashboardService.getGpuRadarSnapshot();

        assertThat(snapshot.getOverallUtilizationRate()).isEqualByComparingTo("25.00");
        assertThat(snapshot.getSystemAvgWaitSeconds()).isEqualByComparingTo("18.00");
        assertThat(snapshot.getSystemFreeMemoryGb()).isEqualByComparingTo("96.00");
        assertThat(snapshot.getNextReleaseAt()).isEqualTo(nextRelease);
        assertThat(snapshot.getNextReleaseInSeconds()).isBetween(59L, 60L);
    }

    @Test
    void getPriorityTop5_shouldKeepAnonymousMappingStable() {
        GpuTask first = GpuTask.builder()
                .id(11L).userId(2L).title("task-a").status(TaskStatus.QUEUED.getCode())
                .basePriority(6).minMemoryGb(new BigDecimal("8")).enqueueAt(LocalDateTime.now().minusMinutes(1))
                .build();
        GpuTask second = GpuTask.builder()
                .id(12L).userId(3L).title("task-b").status(TaskStatus.QUEUED.getCode())
                .basePriority(5).minMemoryGb(new BigDecimal("16")).enqueueAt(LocalDateTime.now().minusMinutes(2))
                .build();
        GpuTask third = GpuTask.builder()
                .id(13L).userId(2L).title("task-c").status(TaskStatus.QUEUED.getCode())
                .basePriority(7).minMemoryGb(new BigDecimal("24")).enqueueAt(LocalDateTime.now().minusMinutes(3))
                .build();
        GpuTask mine = GpuTask.builder()
                .id(14L).userId(1L).title("task-d").status(TaskStatus.QUEUED.getCode())
                .basePriority(8).minMemoryGb(new BigDecimal("32")).enqueueAt(LocalDateTime.now().minusMinutes(4))
                .build();

        when(priorityQueue.topMembers(5)).thenReturn(List.of(11L, 12L, 13L, 14L));
        when(taskMapper.selectBatchIds(List.of(11L, 12L, 13L, 14L))).thenReturn(List.of(first, second, third, mine));

        List<PriorityQueueTopItem> items = taskDashboardService.getPriorityTop5(1L);

        assertThat(items).hasSize(4);
        assertThat(items.get(0).getDisplayName()).startsWith("User_A ");
        assertThat(items.get(1).getDisplayName()).startsWith("User_B ");
        assertThat(items.get(2).getDisplayName()).startsWith("User_A ");
        assertThat(items.get(3).getDisplayName()).isEqualTo("我的任务 task-d");
    }
}

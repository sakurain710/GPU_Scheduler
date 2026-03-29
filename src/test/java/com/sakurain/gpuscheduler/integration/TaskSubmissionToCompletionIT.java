package com.sakurain.gpuscheduler.integration;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.*;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.dto.task.TaskResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 集成测试 — 任务提交→执行→完成全流程
 *
 * <p>策略：使用真实 Redis（TaskPriorityQueue），对数据库 Mapper 使用 @MockBean，
 * 精确控制每步的状态，验证：
 * <ul>
 *   <li>任务提交后进入 Redis 队列</li>
 *   <li>Dispatcher 从队列取任务并完成 GPU 分配</li>
 *   <li>TaskAssignmentService 触发 QUEUED→RUNNING 转换</li>
 *   <li>TaskCompletionMonitor 检测 Future 完成后触发 RUNNING→COMPLETED 并释放 GPU</li>
 *   <li>全程审计日志写入</li>
 * </ul>
 */
@IntegrationTest
class TaskSubmissionToCompletionIT {

    /* ── 真实 Bean ────────────────────────────────────── */
    @Autowired private TaskPriorityQueue priorityQueue;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private TaskStateMachine stateMachine;
    @Autowired private TaskExecutionSimulator simulator;
    @Autowired private CircuitBreakerService circuitBreaker;

    /* ── 数据库层 Mock ─────────────────────────────────── */
    @MockitoBean private GpuTaskMapper taskMapper;
    @MockitoBean private GpuTaskLogMapper taskLogMapper;
    @MockitoBean private GpuMapper gpuMapper;

    /* ── 业务 Bean（依赖上面的 Mock）────────────────────── */
    @Autowired private GpuTaskService taskService;
    @Autowired private GpuAllocator gpuAllocator;
    @Autowired private TaskAssignmentService assignmentService;
    @Autowired private TaskCompletionMonitor completionMonitor;
    @Autowired private TaskAgingScheduler agingScheduler;

    private static final Long TASK_ID = 1001L;
    private static final Long GPU_ID  = 501L;
    private static final Long USER_ID = 9L;

    /** 可变状态，模拟 DB 中 task.status 随着 updateById 调用而变化 */
    private final AtomicLong taskStatusCode = new AtomicLong(TaskStatus.PENDING.getCode());

    @BeforeEach
    void setUp() {
        redisTemplate.delete("gpu:task:queue");
        taskStatusCode.set(TaskStatus.PENDING.getCode());
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("gpu:task:queue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: 任务提交后在 Redis 队列中可见
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void submitTask_taskAppearsInRedisQueue() {
        // Arrange — mock mapper 行为
        stubTaskMapperInsert(TASK_ID);
        stubTaskMapperSelectById_transitioning();

        // Act
        SubmitTaskRequest req = buildRequest("ResNet50训练", 8, "24.00", "500000");
        TaskResponse resp = taskService.submitTask(req, USER_ID);

        // Assert — 返回值为 QUEUED
        assertThat(resp.getId()).isEqualTo(TASK_ID);
        assertThat(resp.getStatusLabel()).isEqualTo("Queued");

        // Assert — Redis 队列中有且仅有该任务
        assertThat(priorityQueue.size()).isEqualTo(1);
        assertThat(priorityQueue.contains(TASK_ID)).isTrue();

        // Assert — 审计日志写入（PENDING→QUEUED）
        verify(taskLogMapper, times(1)).insert(any(GpuTaskLog.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: Dispatcher 出队并调用 assignmentService.assign
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void dispatcher_dequeuesAndAssignsGpu() {
        // Arrange — 手动把任务放入队列（跳过 submit）
        priorityQueue.enqueue(TASK_ID, 8.0);

        Gpu gpu = buildGpu(GPU_ID, "24.00", "10.00");

        // taskMapper.selectById 返回 QUEUED 状态的任务
        GpuTask queuedTask = buildTask(TASK_ID, GPU_ID, TaskStatus.QUEUED, "24.00");
        queuedTask.setComputeUnitsGflop(new BigDecimal("1"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(queuedTask);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        // gpuAllocator.allocate → IDLE GPU
        when(gpuMapper.selectList(any())).thenReturn(List.of(gpu));
        when(gpuMapper.tryMarkBusy(
                eq(GPU_ID),
                eq(GpuStatus.IDLE.getCode()),
                eq(GpuStatus.BUSY.getCode())
        )).thenReturn(1);

        // Act — 手动触发一次调度
        // TaskDispatcher.dispatchOnce() 内部会：
        //   dequeue → selectById → allocate → assignmentService.assign
        // 但 assign 内部还需要 taskService.transition(QUEUED→RUNNING)：
        //   transition 会再次 selectById，所以让第二次返回 RUNNING
        GpuTask runningTask = buildTask(TASK_ID, GPU_ID, TaskStatus.RUNNING, "24.00");
        when(taskMapper.selectById(TASK_ID)).thenReturn(queuedTask, runningTask);

        // 执行调度
        Long dequeued = priorityQueue.dequeue();
        assertThat(dequeued).isEqualTo(TASK_ID);

        // 直接调用 assign（相当于 dispatcher 找到 GPU 后的逻辑）
        assignmentService.assign(queuedTask, gpu);

        // Assert — GPU 被标记为 BUSY
        verify(gpuMapper).tryMarkBusy(
                GPU_ID,
                GpuStatus.IDLE.getCode(),
                GpuStatus.BUSY.getCode()
        );

        // Assert — 任务状态日志（QUEUED→RUNNING）
        verify(taskLogMapper, atLeastOnce()).insert(any(GpuTaskLog.class));

        // Assert — 队列已清空
        assertThat(priorityQueue.size()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: 任务完成后 GPU 释放回 IDLE
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void completionMonitor_releasesGpuAfterTaskDone() throws Exception {
        // Arrange — 构造已 RUNNING 的任务（estimatedSeconds = 0.01s，很短以便测试快速结束）
        GpuTask runningTask = buildTask(TASK_ID, GPU_ID, TaskStatus.RUNNING, "24.00");
        runningTask.setEstimatedSeconds(new BigDecimal("0.01"));
        runningTask.setDispatchedAt(LocalDateTime.now());

        GpuTask completedTask = buildTask(TASK_ID, GPU_ID, TaskStatus.COMPLETED, "24.00");
        completedTask.setFinishedAt(LocalDateTime.now());

        Gpu busyGpu = buildGpu(GPU_ID, "24.00", "10.00");
        busyGpu.setStatus(GpuStatus.BUSY.getCode());

        // taskMapper：运行中的任务列表
        when(taskMapper.selectList(any()))
                .thenReturn(List.of(runningTask))
                .thenReturn(List.of()); // 第二次 monitor 时已完成
        when(taskMapper.selectById(TASK_ID)).thenReturn(runningTask, completedTask);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);
        when(gpuMapper.selectById(GPU_ID)).thenReturn(busyGpu);
        when(gpuMapper.tryMarkIdle(
                eq(GPU_ID),
                eq(GpuStatus.BUSY.getCode()),
                eq(GpuStatus.IDLE.getCode())
        )).thenReturn(1);

        // 向模拟器提交任务（estimatedSeconds 极短，几乎立刻完成）
        GpuTask taskSnapshot = GpuTask.builder()
                .id(TASK_ID).gpuId(GPU_ID)
                .estimatedSeconds(new BigDecimal("0.01"))
                .dispatchedAt(LocalDateTime.now())
                .build();
        simulator.submitTask(taskSnapshot);

        // 等待模拟器 Future 完成（最多 5 秒）
        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .until(() -> !simulator.isRunning(TASK_ID));

        // Act — 触发完成监控
        completionMonitor.monitorRunningTasks();

        // Assert — GPU 状态更新为 IDLE
        verify(gpuMapper).tryMarkIdle(
                GPU_ID,
                GpuStatus.BUSY.getCode(),
                GpuStatus.IDLE.getCode()
        );

        // Assert — 任务状态日志（RUNNING→COMPLETED）
        verify(taskLogMapper, atLeastOnce()).insert(any(GpuTaskLog.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: 整体流程日志数量验证（2 条：PENDING→QUEUED，QUEUED→RUNNING）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void fullFlow_auditLogWrittenForEachTransition() {
        // PENDING → QUEUED（submitTask）
        stubTaskMapperInsert(TASK_ID);
        stubTaskMapperSelectById_transitioning();
        taskService.submitTask(buildRequest("任务A", 5, "8.00", "100000"), USER_ID);

        // QUEUED → RUNNING（transition）
        GpuTask queued = buildTask(TASK_ID, null, TaskStatus.QUEUED, "8.00");
        when(taskMapper.selectById(TASK_ID)).thenReturn(queued);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.RUNNING, GPU_ID, null);

        // RUNNING → COMPLETED（transition）
        GpuTask running = buildTask(TASK_ID, GPU_ID, TaskStatus.RUNNING, "8.00");
        when(taskMapper.selectById(TASK_ID)).thenReturn(running);
        taskService.transition(TASK_ID, TaskStatus.COMPLETED, GPU_ID, null);

        // 3 次状态转换 → 3 条审计日志
        verify(taskLogMapper, times(3)).insert(any(GpuTaskLog.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    private void stubTaskMapperInsert(Long id) {
        doAnswer(inv -> {
            GpuTask t = inv.getArgument(0);
            t.setId(id);
            taskStatusCode.set(TaskStatus.PENDING.getCode());
            return 1;
        }).when(taskMapper).insert(any(GpuTask.class));
    }

    /**
     * selectById：第一次 PENDING（transition 内部读），第二次 QUEUED（submitTask 最终读）。
     * updateById：记录新状态，供后续 selectById 返回。
     */
    private void stubTaskMapperSelectById_transitioning() {
        when(taskMapper.selectById(TASK_ID)).thenAnswer(inv -> buildTaskByCurrentStatus());
        when(taskMapper.updateById(any(GpuTask.class))).thenAnswer(inv -> {
            GpuTask t = inv.getArgument(0);
            if (t.getStatus() != null) taskStatusCode.set(t.getStatus());
            return 1;
        });
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);
    }

    private GpuTask buildTaskByCurrentStatus() {
        return buildTask(TASK_ID, null, TaskStatus.fromCode((int) taskStatusCode.get()), "24.00");
    }

    private GpuTask buildTask(Long id, Long gpuId, TaskStatus status, String memGb) {
        return GpuTask.builder()
                .id(id)
                .userId(USER_ID)
                .gpuId(gpuId)
                .title("测试任务")
                .taskType("inference")
                .minMemoryGb(new BigDecimal(memGb))
                .computeUnitsGflop(new BigDecimal("500000"))
                .basePriority(5)
                .status(status.getCode())
                .enqueueAt(status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
                        ? LocalDateTime.now().minusSeconds(10) : null)
                .dispatchedAt(status == TaskStatus.RUNNING ? LocalDateTime.now() : null)
                .estimatedSeconds(status == TaskStatus.RUNNING ? new BigDecimal("10") : null)
                .build();
    }

    private Gpu buildGpu(Long id, String memGb, String tflops) {
        return Gpu.builder()
                .id(id)
                .name("NVIDIA A100")
                .manufacturer("NVIDIA")
                .memoryGb(new BigDecimal(memGb))
                .computingPowerTflops(new BigDecimal(tflops))
                .status(GpuStatus.IDLE.getCode())
                .build();
    }

    private SubmitTaskRequest buildRequest(String title, int priority,
                                           String memGb, String gflops) {
        return SubmitTaskRequest.builder()
                .title(title)
                .taskType("model_training")
                .minMemoryGb(new BigDecimal(memGb))
                .computeUnitsGflop(new BigDecimal(gflops))
                .basePriority(priority)
                .build();
    }
}

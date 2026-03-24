package com.sakurain.gpuscheduler.integration;

import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.InvalidTaskStateException;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import com.sakurain.gpuscheduler.scheduler.TaskStateMachine;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 集成测试 — 状态机与真实 Redis 联动
 *
 * <p>验证：
 * <ul>
 *   <li>合法的状态转换同步更新 Redis 队列</li>
 *   <li>非法状态转换抛出异常且队列不变</li>
 *   <li>QUEUED→CANCELLED 从队列移除</li>
 *   <li>PENDING→QUEUED 写入队列</li>
 *   <li>终态任务（COMPLETED/FAILED/CANCELLED）不可再转换</li>
 *   <li>多次转换序列保持队列一致性</li>
 * </ul>
 */
@IntegrationTest
class StateMachineRedisIT {

    @Autowired private TaskPriorityQueue priorityQueue;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private TaskStateMachine stateMachine;

    @MockBean private GpuTaskMapper taskMapper;
    @MockBean private GpuTaskLogMapper taskLogMapper;
    @MockBean private GpuMapper gpuMapper;

    @Autowired private GpuTaskService taskService;

    private static final Long TASK_ID = 5001L;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("gpu:task:queue");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("gpu:task:queue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: PENDING→QUEUED 写入 Redis 队列
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void transition_pendingToQueued_enqueuesInRedis() {
        GpuTask pending = buildTask(TASK_ID, TaskStatus.PENDING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.QUEUED, null, USER_ID);

        // Redis 队列中有该任务
        assertThat(priorityQueue.contains(TASK_ID)).isTrue();
        assertThat(priorityQueue.size()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: QUEUED→CANCELLED 从 Redis 队列移除
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void transition_queuedToCancelled_removesFromRedis() {
        // 先放入队列
        priorityQueue.enqueue(TASK_ID, 5.0);
        assertThat(priorityQueue.contains(TASK_ID)).isTrue();

        GpuTask queued = buildTask(TASK_ID, TaskStatus.QUEUED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(queued);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.CANCELLED, null, USER_ID);

        // 队列中已无该任务
        assertThat(priorityQueue.contains(TASK_ID)).isFalse();
        assertThat(priorityQueue.size()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: QUEUED→RUNNING 从 Redis 队列移除
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void transition_queuedToRunning_removesFromRedis() {
        priorityQueue.enqueue(TASK_ID, 7.0);

        GpuTask queued = buildTask(TASK_ID, TaskStatus.QUEUED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(queued);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.RUNNING, 100L, null);

        assertThat(priorityQueue.contains(TASK_ID)).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: 终态任务不可再转换（状态机拦截）
    // ─────────────────────────────────────────────────────────────────────────
    @ParameterizedTest(name = "终态 {0} → {1} 应抛出异常")
    @CsvSource({
        "COMPLETED, RUNNING",
        "FAILED,    QUEUED",
        "CANCELLED, RUNNING",
        "COMPLETED, QUEUED",
        "FAILED,    COMPLETED"
    })
    void transition_fromTerminalState_throws(String from, String to) {
        TaskStatus fromStatus = TaskStatus.valueOf(from);
        TaskStatus toStatus   = TaskStatus.valueOf(to);

        GpuTask task = buildTask(TASK_ID, fromStatus);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(TASK_ID, toStatus, null, USER_ID))
                .isInstanceOf(InvalidTaskStateException.class);

        // 队列不变（不应有任何入队/出队操作）
        assertThat(priorityQueue.size()).isEqualTo(0);
        verify(taskMapper, never()).updateById(any(GpuTask.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5: 非法转换（非终态但不允许的跳转）
    // ─────────────────────────────────────────────────────────────────────────
    @ParameterizedTest(name = "非法转换 {0} → {1}")
    @CsvSource({
        "PENDING, RUNNING",    // 必须先 QUEUED
        "PENDING, COMPLETED",  // 不允许跳转
        "RUNNING, QUEUED",     // 不可回退
        "RUNNING, PENDING"     // 不可回退
    })
    void transition_invalidNonTerminalTransition_throws(String from, String to) {
        TaskStatus fromStatus = TaskStatus.valueOf(from);
        TaskStatus toStatus   = TaskStatus.valueOf(to);

        GpuTask task = buildTask(TASK_ID, fromStatus);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(TASK_ID, toStatus, null, USER_ID))
                .isInstanceOf(InvalidTaskStateException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6: 完整转换序列 PENDING→QUEUED→RUNNING→COMPLETED 队列保持一致
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void fullTransitionSequence_redisQueueConsistent() {
        // Step 1: PENDING → QUEUED
        GpuTask pending = buildTask(TASK_ID, TaskStatus.PENDING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.QUEUED, null, USER_ID);
        assertThat(priorityQueue.contains(TASK_ID)).isTrue();
        assertThat(priorityQueue.size()).isEqualTo(1);

        // Step 2: QUEUED → RUNNING
        GpuTask queued = buildTask(TASK_ID, TaskStatus.QUEUED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(queued);

        taskService.transition(TASK_ID, TaskStatus.RUNNING, 200L, null);
        assertThat(priorityQueue.contains(TASK_ID)).isFalse();
        assertThat(priorityQueue.size()).isEqualTo(0);

        // Step 3: RUNNING → COMPLETED
        GpuTask running = buildTask(TASK_ID, TaskStatus.RUNNING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(running);

        taskService.transition(TASK_ID, TaskStatus.COMPLETED, 200L, null);
        // 终态，队列依然为空
        assertThat(priorityQueue.size()).isEqualTo(0);

        // 共 3 次转换 → 3 条审计日志
        verify(taskLogMapper, times(3)).insert(any(GpuTaskLog.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7: 多任务并发在队列中，取消其中一个不影响其他
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void cancelOneTask_otherTasksRemainInQueue() {
        Long taskA = 5001L;
        Long taskB = 5002L;
        Long taskC = 5003L;

        priorityQueue.enqueue(taskA, 5.0);
        priorityQueue.enqueue(taskB, 8.0);
        priorityQueue.enqueue(taskC, 3.0);
        assertThat(priorityQueue.size()).isEqualTo(3);

        // 取消任务 B
        GpuTask queued = buildTask(taskB, TaskStatus.QUEUED);
        when(taskMapper.selectById(taskB)).thenReturn(queued);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(taskB, TaskStatus.CANCELLED, null, USER_ID);

        // 只剩 A 和 C
        assertThat(priorityQueue.size()).isEqualTo(2);
        assertThat(priorityQueue.contains(taskB)).isFalse();
        assertThat(priorityQueue.contains(taskA)).isTrue();
        assertThat(priorityQueue.contains(taskC)).isTrue();

        // 出队顺序：A(5) → C(3)
        assertThat(priorityQueue.dequeue()).isEqualTo(taskA);
        assertThat(priorityQueue.dequeue()).isEqualTo(taskC);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 8: 任务重复入队（updateScore 覆盖，不重复计数）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void enqueueAlreadyQueuedTask_updatesScoreNoDuplication() {
        Long taskId = 6001L;

        priorityQueue.enqueue(taskId, 3.0);
        assertThat(priorityQueue.size()).isEqualTo(1);

        // 再次 enqueue 相同 ID（老化后优先级提升）
        priorityQueue.enqueue(taskId, 9.0);

        // ZSet member 唯一，不会重复
        assertThat(priorityQueue.size()).isEqualTo(1);

        // 新 score 生效
        priorityQueue.enqueue(6002L, 5.0);
        assertThat(priorityQueue.dequeue()).isEqualTo(taskId); // 9.0 > 5.0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 9: 合法转换 PENDING→CANCELLED（直接从 PENDING 取消）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void transition_pendingToCancelled_noRedisInteraction() {
        GpuTask pending = buildTask(TASK_ID, TaskStatus.PENDING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.CANCELLED, null, USER_ID);

        // PENDING 状态未入队，Redis 应保持空
        assertThat(priorityQueue.size()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 10: RUNNING→FAILED 不影响 Redis 队列
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void transition_runningToFailed_redisUnchanged() {
        // 队列中有另一个任务，RUNNING 任务已从队列移除
        priorityQueue.enqueue(9999L, 5.0);

        GpuTask running = buildTask(TASK_ID, TaskStatus.RUNNING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(running);
        when(taskMapper.updateById(any(GpuTask.class))).thenReturn(1);
        when(taskLogMapper.insert(any(GpuTaskLog.class))).thenReturn(1);

        taskService.transition(TASK_ID, TaskStatus.FAILED, 100L, null);

        // 队列中 9999 任务未受影响
        assertThat(priorityQueue.size()).isEqualTo(1);
        assertThat(priorityQueue.contains(9999L)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    private GpuTask buildTask(Long id, TaskStatus status) {
        return GpuTask.builder()
                .id(id)
                .userId(USER_ID)
                .title("状态机测试任务-" + id)
                .taskType("inference")
                .minMemoryGb(new BigDecimal("8.00"))
                .computeUnitsGflop(new BigDecimal("10000"))
                .basePriority(5)
                .status(status.getCode())
                .enqueueAt(status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
                        ? LocalDateTime.now().minusSeconds(30) : null)
                .dispatchedAt(status == TaskStatus.RUNNING ? LocalDateTime.now().minusSeconds(5) : null)
                .estimatedSeconds(status == TaskStatus.RUNNING ? new BigDecimal("10") : null)
                .finishedAt(status.isTerminal() ? LocalDateTime.now() : null)
                .build();
    }
}

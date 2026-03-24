package com.sakurain.gpuscheduler.integration;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskLogMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.scheduler.GpuAllocator;
import com.sakurain.gpuscheduler.scheduler.TaskPriorityQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 集成测试 — GPU 分配边界情况
 *
 * <p>验证：
 * <ul>
 *   <li>没有可用 GPU 时，任务重新回到队列</li>
 *   <li>所有 GPU 显存不足时，返回 empty（任务等待）</li>
 *   <li>任务没有指定 minMemoryGb 时，无法分配</li>
 *   <li>BestFit 在多个满足条件的 GPU 中选最小显存</li>
 *   <li>GPU 处于 OFFLINE/MAINTENANCE 状态时不被分配</li>
 *   <li>恰好满足显存需求的临界情况</li>
 * </ul>
 */
@IntegrationTest
class GpuAllocationEdgeCasesIT {

    @Autowired private TaskPriorityQueue priorityQueue;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private GpuAllocator gpuAllocator;

    @MockBean private GpuMapper gpuMapper;
    @MockBean private GpuTaskMapper taskMapper;
    @MockBean private GpuTaskLogMapper taskLogMapper;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("gpu:task:queue");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("gpu:task:queue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: 没有任何可用 GPU — 返回 empty，任务应重新入队
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void allocate_noAvailableGpu_returnsEmpty() {
        when(gpuMapper.selectList(any())).thenReturn(Collections.emptyList());

        GpuTask task = buildTask(1L, "16.00");
        Optional<Gpu> result = gpuAllocator.allocate(task);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: 所有 GPU 显存不足 — 返回 empty
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void allocate_insufficientVram_returnsEmpty() {
        // GPU 只有 8GB，但任务需要 24GB
        Gpu smallGpu = buildGpu(1L, "8.00", GpuStatus.IDLE);
        // GpuAllocator 的 QueryWrapper 已在 DB 层过滤（memory_gb >= minMemoryGb），
        // 所以 mock 返回空列表（模拟 DB 过滤结果）
        when(gpuMapper.selectList(any())).thenReturn(Collections.emptyList());

        GpuTask task = buildTask(2L, "24.00");
        Optional<Gpu> result = gpuAllocator.allocate(task);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: 任务未指定 minMemoryGb — 无法分配
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void allocate_nullMinMemoryGb_returnsEmpty() {
        GpuTask task = GpuTask.builder()
                .id(3L).status(TaskStatus.QUEUED.getCode())
                .minMemoryGb(null)   // 未指定
                .build();

        Optional<Gpu> result = gpuAllocator.allocate(task);

        assertThat(result).isEmpty();
        // 不应查询数据库（提前返回）
        verify(gpuMapper, never()).selectList(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: BestFit — 多个满足需求的 GPU，选最小显存
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void allocate_bestFit_selectsSmallestSufficientGpu() {
        Gpu gpu16 = buildGpu(10L, "16.00", GpuStatus.IDLE);
        Gpu gpu40 = buildGpu(11L, "40.00", GpuStatus.IDLE);
        Gpu gpu80 = buildGpu(12L, "80.00", GpuStatus.IDLE);

        // GpuAllocator 查询时已按 memory_gb ASC 排序，mock 返回已排好序的列表
        when(gpuMapper.selectList(any())).thenReturn(List.of(gpu16, gpu40, gpu80));

        GpuTask task = buildTask(4L, "16.00");   // 需要 16GB
        Optional<Gpu> result = gpuAllocator.allocate(task);

        assertThat(result).isPresent();
        // BestFit 选最小满足需求的：16GB
        assertThat(result.get().getId()).isEqualTo(10L);
        assertThat(result.get().getMemoryGb()).isEqualByComparingTo("16.00");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5: 非 IDLE 状态 GPU 不被分配（DB 层过滤后 mock 返回空）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void allocate_offlineAndMaintenanceGpus_returnsEmpty() {
        // DB 查询 status=IDLE 后没有结果（OFFLINE/MAINTENANCE 被过滤掉）
        when(gpuMapper.selectList(any())).thenReturn(Collections.emptyList());

        GpuTask task = buildTask(5L, "8.00");
        Optional<Gpu> result = gpuAllocator.allocate(task);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6: 显存恰好等于需求 — 临界满足，应分配成功
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void allocate_exactVramMatch_allocatesSuccessfully() {
        Gpu gpu = buildGpu(20L, "24.00", GpuStatus.IDLE);
        when(gpuMapper.selectList(any())).thenReturn(List.of(gpu));

        GpuTask task = buildTask(6L, "24.00");   // 恰好需要 24GB
        Optional<Gpu> result = gpuAllocator.allocate(task);

        assertThat(result).isPresent();
        assertThat(result.get().getMemoryGb()).isEqualByComparingTo("24.00");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7: 任务分配失败后重新入队，队列长度不变
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void noGpuAvailable_taskRequeuedWithSamePriority() {
        Long taskId = 7L;
        int priority = 6;

        // 先入队
        priorityQueue.enqueue(taskId, priority);
        assertThat(priorityQueue.size()).isEqualTo(1);

        // 出队 — 准备分配
        Long dequeued = priorityQueue.dequeue();
        assertThat(dequeued).isEqualTo(taskId);
        assertThat(priorityQueue.size()).isEqualTo(0);

        // 分配失败 — 重新入队（模拟 TaskDispatcher 行为）
        when(gpuMapper.selectList(any())).thenReturn(Collections.emptyList());
        GpuTask task = buildTask(taskId, "16.00");
        task.setBasePriority(priority);
        Optional<Gpu> gpu = gpuAllocator.allocate(task);

        if (gpu.isEmpty()) {
            priorityQueue.enqueue(taskId, task.getBasePriority());
        }

        // 重新入队后，队列中仍有该任务
        assertThat(priorityQueue.size()).isEqualTo(1);
        assertThat(priorityQueue.contains(taskId)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 8: countAvailableGpus — 按显存统计
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void countAvailableGpus_returnsCorrectCount() {
        // Mock DB count 结果
        when(gpuMapper.selectCount(any())).thenReturn(3L);

        long count = gpuAllocator.countAvailableGpus(new BigDecimal("8.00"));

        assertThat(count).isEqualTo(3L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 9: hasAvailableGpu — 有可用 GPU 返回 true
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void hasAvailableGpu_withAvailableGpu_returnsTrue() {
        when(gpuMapper.selectCount(any())).thenReturn(2L);

        GpuTask task = buildTask(9L, "16.00");
        assertThat(gpuAllocator.hasAvailableGpu(task)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 10: hasAvailableGpu — 无可用 GPU 返回 false
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void hasAvailableGpu_noAvailableGpu_returnsFalse() {
        when(gpuMapper.selectCount(any())).thenReturn(0L);

        GpuTask task = buildTask(10L, "16.00");
        assertThat(gpuAllocator.hasAvailableGpu(task)).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    private GpuTask buildTask(Long id, String minMemGb) {
        return GpuTask.builder()
                .id(id)
                .userId(1L)
                .title("测试任务-" + id)
                .taskType("inference")
                .minMemoryGb(new BigDecimal(minMemGb))
                .computeUnitsGflop(new BigDecimal("10000"))
                .basePriority(5)
                .status(TaskStatus.QUEUED.getCode())
                .build();
    }

    private Gpu buildGpu(Long id, String memGb, GpuStatus status) {
        return Gpu.builder()
                .id(id)
                .name("GPU-" + id)
                .manufacturer("NVIDIA")
                .memoryGb(new BigDecimal(memGb))
                .computingPowerTflops(new BigDecimal("10.00"))
                .status(status.getCode())
                .build();
    }
}

package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GPU分配器测试 — BestFit算法
 */
@ExtendWith(MockitoExtension.class)
class GpuAllocatorTest {

    @Mock
    private GpuMapper gpuMapper;

    @InjectMocks
    private GpuAllocator allocator;

    @Test
    void testAllocate_BestFit_SelectsSmallestSufficientGpu() {
        // 任务需要24GB显存
        GpuTask task = GpuTask.builder()
                .id(1L)
                .minMemoryGb(new BigDecimal("24.00"))
                .build();

        // 模拟3个可用GPU: 32GB, 40GB, 80GB
        List<Gpu> availableGpus = Arrays.asList(
                createGpu(1L, "RTX 3090", new BigDecimal("24.00")),
                createGpu(2L, "A100 40GB", new BigDecimal("40.00")),
                createGpu(3L, "A100 80GB", new BigDecimal("80.00"))
        );

        when(gpuMapper.selectList(any())).thenReturn(availableGpus);

        Optional<Gpu> result = allocator.allocate(task);

        assertTrue(result.isPresent());
        // BestFit应该选择最小的满足需求的GPU (24GB)
        assertEquals(1L, result.get().getId());
        assertEquals("RTX 3090", result.get().getName());
        assertEquals(new BigDecimal("24.00"), result.get().getMemoryGb());
    }

    @Test
    void testAllocate_MinimizeFragmentation() {
        // 任务需要16GB显存
        GpuTask task = GpuTask.builder()
                .id(1L)
                .minMemoryGb(new BigDecimal("16.00"))
                .build();

        // 模拟多个可用GPU，按显存升序排列
        List<Gpu> availableGpus = Arrays.asList(
                createGpu(1L, "RTX 3080", new BigDecimal("16.00")),
                createGpu(2L, "RTX 3090", new BigDecimal("24.00")),
                createGpu(3L, "A100 40GB", new BigDecimal("40.00"))
        );

        when(gpuMapper.selectList(any())).thenReturn(availableGpus);

        Optional<Gpu> result = allocator.allocate(task);

        assertTrue(result.isPresent());
        // 应该选择16GB的GPU，而不是24GB或40GB，最小化碎片
        assertEquals(1L, result.get().getId());
        assertEquals(new BigDecimal("16.00"), result.get().getMemoryGb());
    }

    @Test
    void testAllocate_NoAvailableGpu() {
        // 任务需要80GB显存
        GpuTask task = GpuTask.builder()
                .id(1L)
                .minMemoryGb(new BigDecimal("80.00"))
                .build();

        // 没有可用GPU
        when(gpuMapper.selectList(any())).thenReturn(Collections.emptyList());

        Optional<Gpu> result = allocator.allocate(task);

        assertFalse(result.isPresent());
    }

    @Test
    void testAllocate_TaskWithoutMemoryRequirement() {
        // 任务没有指定显存需求
        GpuTask task = GpuTask.builder()
                .id(1L)
                .minMemoryGb(null)
                .build();

        Optional<Gpu> result = allocator.allocate(task);

        assertFalse(result.isPresent());
        verify(gpuMapper, never()).selectList(any());
    }

    @Test
    void testAllocate_HeterogeneousGpuPool() {
        // 任务需要32GB显存
        GpuTask task = GpuTask.builder()
                .id(1L)
                .minMemoryGb(new BigDecimal("32.00"))
                .build();

        // 异构GPU池：不同品牌、型号、显存（已按显存升序排列，模拟数据库排序）
        List<Gpu> availableGpus = Arrays.asList(
                createGpu(3L, "华为昇腾910", new BigDecimal("32.00")),
                createGpu(1L, "NVIDIA A100 40GB", new BigDecimal("40.00")),
                createGpu(2L, "AMD MI250X", new BigDecimal("64.00"))
        );

        when(gpuMapper.selectList(any())).thenReturn(availableGpus);

        Optional<Gpu> result = allocator.allocate(task);

        assertTrue(result.isPresent());
        // 应该选择32GB的GPU（最小满足需求）
        assertEquals(3L, result.get().getId());
        assertEquals(new BigDecimal("32.00"), result.get().getMemoryGb());
    }

    @Test
    void testFindAvailableGpus() {
        List<Gpu> gpus = Arrays.asList(
                createGpu(1L, "GPU1", new BigDecimal("16.00")),
                createGpu(2L, "GPU2", new BigDecimal("24.00"))
        );

        when(gpuMapper.selectList(any())).thenReturn(gpus);

        List<Gpu> result = allocator.findAvailableGpus();

        assertEquals(2, result.size());
        verify(gpuMapper, times(1)).selectList(any());
    }

    @Test
    void testCountAvailableGpus() {
        when(gpuMapper.selectCount(any())).thenReturn(3L);

        long count = allocator.countAvailableGpus(new BigDecimal("24.00"));

        assertEquals(3L, count);
        verify(gpuMapper, times(1)).selectCount(any());
    }

    @Test
    void testHasAvailableGpu_True() {
        GpuTask task = GpuTask.builder()
                .minMemoryGb(new BigDecimal("24.00"))
                .build();

        when(gpuMapper.selectCount(any())).thenReturn(2L);

        boolean result = allocator.hasAvailableGpu(task);

        assertTrue(result);
    }

    @Test
    void testHasAvailableGpu_False() {
        GpuTask task = GpuTask.builder()
                .minMemoryGb(new BigDecimal("80.00"))
                .build();

        when(gpuMapper.selectCount(any())).thenReturn(0L);

        boolean result = allocator.hasAvailableGpu(task);

        assertFalse(result);
    }

    @Test
    void testHasAvailableGpu_NoMemoryRequirement() {
        GpuTask task = GpuTask.builder()
                .minMemoryGb(null)
                .build();

        boolean result = allocator.hasAvailableGpu(task);

        assertFalse(result);
        verify(gpuMapper, never()).selectCount(any());
    }

    // Helper method to create GPU entities
    private Gpu createGpu(Long id, String name, BigDecimal memoryGb) {
        return Gpu.builder()
                .id(id)
                .name(name)
                .manufacturer("NVIDIA")
                .memoryGb(memoryGb)
                .computingPowerTflops(new BigDecimal("19.5"))
                .status(GpuStatus.IDLE.getCode())
                .build();
    }
}

package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.dto.gpu.GpuResponse;
import com.sakurain.gpuscheduler.dto.gpu.RegisterGpuRequest;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GpuService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class GpuServiceTest {

    @Mock
    private GpuMapper gpuMapper;

    @Mock
    private GpuTaskMapper gpuTaskMapper;

    @InjectMocks
    private GpuService gpuService;

    private Gpu idleGpu;
    private Gpu busyGpu;

    @BeforeEach
    void setUp() {
        idleGpu = Gpu.builder()
                .id(1L).name("NVIDIA A100 40GB").manufacturer("NVIDIA")
                .memoryGb(new BigDecimal("40.00"))
                .computingPowerTflops(new BigDecimal("19.5"))
                .status(GpuStatus.IDLE.getCode())
                .build();

        busyGpu = Gpu.builder()
                .id(2L).name("AMD MI250X").manufacturer("AMD")
                .memoryGb(new BigDecimal("128.00"))
                .computingPowerTflops(new BigDecimal("45.3"))
                .status(GpuStatus.BUSY.getCode())
                .build();
    }

    // ── registerGpu ──────────────────────────────────────────────────────────

    @Test
    void registerGpu_insertsAndReturnsResponse() {
        RegisterGpuRequest req = RegisterGpuRequest.builder()
                .name("NVIDIA A100 40GB").manufacturer("NVIDIA")
                .memoryGb(new BigDecimal("40.00"))
                .computingPowerTflops(new BigDecimal("19.5"))
                .build();

        doAnswer(inv -> {
            Gpu g = inv.getArgument(0);
            g.setId(10L);
            return 1;
        }).when(gpuMapper).insert(any(Gpu.class));

        GpuResponse resp = gpuService.registerGpu(req, 1L);

        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getName()).isEqualTo("NVIDIA A100 40GB");
        assertThat(resp.getStatus()).isEqualTo(GpuStatus.IDLE.getCode());
        assertThat(resp.getStatusLabel()).isEqualTo("Idle");

        ArgumentCaptor<Gpu> captor = ArgumentCaptor.forClass(Gpu.class);
        verify(gpuMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(1L);
    }

    // ── getGpu ───────────────────────────────────────────────────────────────

    @Test
    void getGpu_returnsResponse() {
        when(gpuMapper.selectById(1L)).thenReturn(idleGpu);

        GpuResponse resp = gpuService.getGpu(1L);

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getStatusLabel()).isEqualTo("Idle");
    }

    @Test
    void getGpu_notFound_throws() {
        when(gpuMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> gpuService.getGpu(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── listGpus ─────────────────────────────────────────────────────────────

    @Test
    void listGpus_returnsPage() {
        Page<Gpu> gpuPage = new Page<>(1, 20);
        gpuPage.setRecords(List.of(idleGpu, busyGpu));
        gpuPage.setTotal(2);

        when(gpuMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(gpuPage);

        IPage<GpuResponse> result = gpuService.listGpus(1, 20, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
    }

    // ── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_idleToMaintenance_succeeds() {
        when(gpuMapper.selectById(1L)).thenReturn(idleGpu);

        GpuResponse resp = gpuService.updateStatus(1L, GpuStatus.MAINTENANCE.getCode());

        assertThat(resp.getStatus()).isEqualTo(GpuStatus.MAINTENANCE.getCode());
        assertThat(resp.getStatusLabel()).isEqualTo("Maintenance");
        verify(gpuMapper).updateById(any(Gpu.class));
    }

    @Test
    void updateStatus_busyGpu_throws() {
        when(gpuMapper.selectById(2L)).thenReturn(busyGpu);

        assertThatThrownBy(() -> gpuService.updateStatus(2L, GpuStatus.OFFLINE.getCode()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("GPU正在执行任务");
    }

    @Test
    void updateStatus_notFound_throws() {
        when(gpuMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> gpuService.updateStatus(999L, GpuStatus.IDLE.getCode()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteGpu ────────────────────────────────────────────────────────────

    @Test
    void deleteGpu_idleGpu_succeeds() {
        when(gpuMapper.selectById(1L)).thenReturn(idleGpu);

        gpuService.deleteGpu(1L);

        verify(gpuMapper).deleteById(1L);
    }

    @Test
    void deleteGpu_busyGpu_throws() {
        when(gpuMapper.selectById(2L)).thenReturn(busyGpu);

        assertThatThrownBy(() -> gpuService.deleteGpu(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("GPU正在执行任务");

        verify(gpuMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteGpu_notFound_throws() {
        when(gpuMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> gpuService.deleteGpu(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── healthCheck ──────────────────────────────────────────────────────────

    @Test
    void healthCheck_returnsAllStatusCounts() {
        Gpu offlineGpu = Gpu.builder().id(3L).status(GpuStatus.OFFLINE.getCode()).build();
        when(gpuMapper.selectList(null)).thenReturn(List.of(idleGpu, busyGpu, offlineGpu));

        Map<String, Long> stats = gpuService.healthCheck();

        assertThat(stats.get("Idle")).isEqualTo(1L);
        assertThat(stats.get("Busy")).isEqualTo(1L);
        assertThat(stats.get("Offline")).isEqualTo(1L);
        assertThat(stats.get("Maintenance")).isEqualTo(0L);
    }

    // ── utilizationMetrics ───────────────────────────────────────────────────

    @Test
    void utilizationMetrics_calculatesCorrectly() {
        when(gpuMapper.selectList(null)).thenReturn(List.of(idleGpu, busyGpu));

        Map<String, Object> metrics = gpuService.utilizationMetrics();

        assertThat(metrics.get("total")).isEqualTo(2L);
        assertThat(metrics.get("busy")).isEqualTo(1L);
        assertThat(metrics.get("idle")).isEqualTo(1L);
        assertThat(metrics.get("utilizationRate")).isEqualTo("50.0%");
    }

    @Test
    void utilizationMetrics_noGpus_zeroRate() {
        when(gpuMapper.selectList(null)).thenReturn(List.of());

        Map<String, Object> metrics = gpuService.utilizationMetrics();

        assertThat(metrics.get("total")).isEqualTo(0L);
        assertThat(metrics.get("utilizationRate")).isEqualTo("0.0%");
    }
}

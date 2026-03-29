package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.dto.gpu.GpuResponse;
import com.sakurain.gpuscheduler.dto.gpu.RegisterGpuRequest;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.util.PaginationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GPU资源管理服务
 * <p>
 * 提供GPU的CRUD、状态管理、健康检查和利用率统计
 */
@Slf4j
@Service
public class GpuService {

    private final GpuMapper gpuMapper;
    private final GpuTaskMapper gpuTaskMapper;

    public GpuService(GpuMapper gpuMapper, GpuTaskMapper gpuTaskMapper) {
        this.gpuMapper = gpuMapper;
        this.gpuTaskMapper = gpuTaskMapper;
    }

    /**
     * 注册新GPU（初始状态为IDLE）
     */
    @Transactional
    public GpuResponse registerGpu(RegisterGpuRequest request, Long operatorId) {
        Gpu gpu = Gpu.builder()
                .name(request.getName())
                .manufacturer(request.getManufacturer())
                .memoryGb(request.getMemoryGb())
                .computingPowerTflops(request.getComputingPowerTflops())
                .status(GpuStatus.IDLE.getCode())
                .remark(request.getRemark())
                .createdBy(operatorId)
                .build();
        gpuMapper.insert(gpu);
        log.info("GPU注册成功: id={}, name={}", gpu.getId(), gpu.getName());
        return toResponse(gpu);
    }

    /**
     * 查询GPU详情
     */
    public GpuResponse getGpu(Long gpuId) {
        Gpu gpu = gpuMapper.selectById(gpuId);
        if (gpu == null) {
            throw new ResourceNotFoundException("GPU不存在: " + gpuId);
        }
        return toResponse(gpu);
    }

    /**
     * 分页查询GPU列表，支持按状态过滤
     */
    public IPage<GpuResponse> listGpus(Integer page, Integer size, Integer status) {
        Page<Gpu> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 20, 200)
        );
        LambdaQueryWrapper<Gpu> query = new LambdaQueryWrapper<Gpu>()
                .orderByAsc(Gpu::getId);
        if (status != null) {
            query.eq(Gpu::getStatus, status);
        }
        IPage<Gpu> gpuPage = gpuMapper.selectPage(pageParam, query);
        return gpuPage.convert(this::toResponse);
    }

    /**
     * 更新GPU状态
     * <p>
     * 限制：BUSY状态的GPU不允许手动修改（由调度器管理）
     */
    @Transactional
    public GpuResponse updateStatus(Long gpuId, Integer newStatus) {
        Gpu gpu = gpuMapper.selectById(gpuId);
        if (gpu == null) {
            throw new ResourceNotFoundException("GPU不存在: " + gpuId);
        }
        if (gpu.getStatus().equals(GpuStatus.BUSY.getCode())) {
            throw new com.sakurain.gpuscheduler.exception.BusinessException(
                    "GPU_BUSY", "GPU正在执行任务，不允许手动修改状态", 400);
        }
        GpuStatus.fromCode(newStatus); // 校验状态码合法性
        gpu.setStatus(newStatus);
        gpuMapper.updateById(gpu);
        log.info("GPU状态更新: id={}, status={}", gpuId, GpuStatus.fromCode(newStatus).getLabel());
        return toResponse(gpu);
    }

    /**
     * 删除GPU（软删除）
     * <p>
     * 限制：BUSY状态的GPU不允许删除
     */
    @Transactional
    public void deleteGpu(Long gpuId) {
        Gpu gpu = gpuMapper.selectById(gpuId);
        if (gpu == null) {
            throw new ResourceNotFoundException("GPU不存在: " + gpuId);
        }
        if (gpu.getStatus().equals(GpuStatus.BUSY.getCode())) {
            throw new com.sakurain.gpuscheduler.exception.BusinessException(
                    "GPU_BUSY", "GPU正在执行任务，不允许删除", 400);
        }
        gpuMapper.deleteById(gpuId);
        log.info("GPU删除成功: id={}, name={}", gpuId, gpu.getName());
    }

    /**
     * 健康检查 — 返回各状态GPU数量统计
     */
    public Map<String, Long> healthCheck() {
        List<Gpu> all = gpuMapper.selectList(null);
        Map<String, Long> stats = all.stream().collect(
                Collectors.groupingBy(
                        g -> GpuStatus.fromCode(g.getStatus()).getLabel(),
                        Collectors.counting()
                )
        );
        // 确保所有状态都有值（即使为0）
        for (GpuStatus s : GpuStatus.values()) {
            stats.putIfAbsent(s.getLabel(), 0L);
        }
        return stats;
    }

    /**
     * 利用率统计 — 返回BUSY GPU占总GPU的百分比
     */
    public Map<String, Object> utilizationMetrics() {
        List<Gpu> all = gpuMapper.selectList(null);
        long total = all.size();
        long busy = all.stream()
                .filter(g -> g.getStatus().equals(GpuStatus.BUSY.getCode()))
                .count();
        long idle = all.stream()
                .filter(g -> g.getStatus().equals(GpuStatus.IDLE.getCode()))
                .count();

        double utilizationRate = total == 0 ? 0.0 : (double) busy / total * 100;

        return Map.of(
                "total", total,
                "busy", busy,
                "idle", idle,
                "utilizationRate", String.format("%.1f%%", utilizationRate)
        );
    }

    private GpuResponse toResponse(Gpu gpu) {
        GpuStatus status = GpuStatus.fromCode(gpu.getStatus());
        return GpuResponse.builder()
                .id(gpu.getId())
                .name(gpu.getName())
                .manufacturer(gpu.getManufacturer())
                .memoryGb(gpu.getMemoryGb())
                .computingPowerTflops(gpu.getComputingPowerTflops())
                .status(gpu.getStatus())
                .statusLabel(status.getLabel())
                .remark(gpu.getRemark())
                .createdBy(gpu.getCreatedBy())
                .createdAt(gpu.getCreatedAt())
                .updatedAt(gpu.getUpdatedAt())
                .build();
    }
}

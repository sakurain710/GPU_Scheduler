package com.sakurain.gpuscheduler.dto.monitor;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * GPU 指标 DTO
 */
@Data
@Builder
public class GpuMetrics {

    /** GPU 总数 */
    private long total;

    /** 各状态 GPU 数量 */
    private Map<String, Long> countByStatus;

    /** 整体利用率（BUSY / total） */
    private String utilizationRate;

    /** 各 GPU 的显存碎片化分析（gpuId → fragmentation ratio 0.0–1.0） */
    private Map<Long, BigDecimal> vramFragmentationByGpu;

    /** 各 GPU 空闲时长估计（gpuId → idle seconds since last task） */
    private Map<Long, Long> idleSecondsByGpu;
}

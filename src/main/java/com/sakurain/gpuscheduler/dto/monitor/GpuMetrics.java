package com.sakurain.gpuscheduler.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * GPU 指标 DTO
 */
@Schema(description = "GPU metrics")
@Data
@Builder
public class GpuMetrics {

    /** GPU 总数 */
    @Schema(description = "Total GPU count")
    private long total;

    /** 各状态 GPU 数量 */
    @Schema(description = "GPU count by status")
    private Map<String, Long> countByStatus;

    /** 整体利用率（BUSY / total） */
    @Schema(description = "Overall utilization rate")
    private String utilizationRate;


    @Schema(description = "Used VRAM by GPU (GB)")
    private Map<Long, BigDecimal> usedMemoryGbByGpu;

    @Schema(description = "Remaining VRAM by GPU (GB)")
    private Map<Long, BigDecimal> remainingMemoryGbByGpu;

    /** 各 GPU 的显存碎片化分析（gpuId → fragmentation ratio 0.0–1.0） */
    @Schema(description = "VRAM fragmentation ratio by busy GPU")
    private Map<Long, BigDecimal> vramFragmentationByGpu;

    /** 各 GPU 空闲时长估计（gpuId → idle seconds since last task） */
    @Schema(description = "Idle seconds by idle GPU")
    private Map<Long, Long> idleSecondsByGpu;
}

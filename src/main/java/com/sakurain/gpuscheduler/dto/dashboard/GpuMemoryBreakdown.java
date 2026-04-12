package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "Per-GPU memory breakdown")
public class GpuMemoryBreakdown {

    @Schema(description = "GPU id")
    private Long gpuId;

    @Schema(description = "GPU name")
    private String gpuName;

    @Schema(description = "GPU status label")
    private String status;

    @Schema(description = "Total memory in GB")
    private BigDecimal totalMemoryGb;

    @Schema(description = "Allocated and used memory in GB")
    private BigDecimal usedAllocatedMemoryGb;

    @Schema(description = "Fragmented memory in GB")
    private BigDecimal fragmentedMemoryGb;

    @Schema(description = "Free memory in GB")
    private BigDecimal freeMemoryGb;
}

package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "Best-fit memory fragmentation response")
public class MemoryFragmentationResponse {

    @Schema(description = "Allocated and used memory in GB")
    private BigDecimal usedAllocatedMemoryGb;

    @Schema(description = "Fragmented memory in GB")
    private BigDecimal fragmentedMemoryGb;

    @Schema(description = "Free memory in GB")
    private BigDecimal freeMemoryGb;

    @Schema(description = "Per-GPU memory breakdowns")
    private List<GpuMemoryBreakdown> gpuBreakdowns;
}

package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Structured dead-letter queue list response")
public class DlqListResponse {

    @Schema(description = "Current page")
    private long current;

    @Schema(description = "Page size")
    private long size;

    @Schema(description = "Total item count")
    private long total;

    @Schema(description = "Paged DLQ items")
    private List<DlqItemResponse> records;
}

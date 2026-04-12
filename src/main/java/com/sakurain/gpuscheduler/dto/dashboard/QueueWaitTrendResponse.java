package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "Queue wait trend response")
public class QueueWaitTrendResponse {

    @Schema(description = "Trend mode", example = "DAY")
    private String mode;

    @Schema(description = "Anchor date")
    private LocalDate anchorDate;

    @Schema(description = "Bucket unit", example = "HOUR")
    private String bucketUnit;

    @Schema(description = "Trend points")
    private List<QueueWaitTrendPoint> points;
}

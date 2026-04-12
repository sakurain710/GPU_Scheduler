package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Dead-letter queue summary")
public class DlqSummary {

    @Schema(description = "DLQ size")
    private long size;

    @Schema(description = "Latest DLQ entry time")
    private LocalDateTime latestEnteredDlqAt;
}

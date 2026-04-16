package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Task execution log summary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLogTaskSummary {

    @Schema(description = "Task id")
    private Long id;

    @Schema(description = "Current GPU id")
    private Long gpuId;

    @Schema(description = "Current GPU model name")
    private String gpuLabel;

    @Schema(description = "Task submitter id")
    private Long operatorId;

    @Schema(description = "Task submitter display name")
    private String operatorName;
}

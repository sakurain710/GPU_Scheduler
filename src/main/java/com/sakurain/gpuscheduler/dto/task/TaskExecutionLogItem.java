package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "Task execution log item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLogItem {

    @Schema(description = "Event type")
    private String event;

    @Schema(description = "Old status code")
    private Integer oldStatus;

    @Schema(description = "Old status label")
    private String oldStatusLabel;

    @Schema(description = "New status code")
    private Integer newStatus;

    @Schema(description = "New status label")
    private String newStatusLabel;

    @Schema(description = "Related GPU id")
    private Long gpuId;

    @Schema(description = "Operator id")
    private Long operatorId;

    @Schema(description = "Operator display name")
    private String operatorName;

    @Schema(description = "Detail message")
    private String detail;

    @Schema(description = "Event time")
    private LocalDateTime createdAt;
}

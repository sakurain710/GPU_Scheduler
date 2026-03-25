package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GPU任务响应
 */

@Schema(description = "Task response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    private Long userId;
    private Long gpuId;
    private String title;
    private String description;
    private String taskType;
    private BigDecimal minMemoryGb;
    private BigDecimal computeUnitsGflop;
    private Integer basePriority;
    private Integer status;
    private String statusLabel;
    private BigDecimal estimatedSeconds;
    private BigDecimal actualSeconds;
    private String errorMessage;
    private LocalDateTime enqueueAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime estimatedFinishAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}

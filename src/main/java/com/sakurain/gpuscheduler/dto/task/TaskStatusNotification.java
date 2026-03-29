package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务状态变更通知
 */
@Data
@Builder
@Schema(description = "Task status notification")
public class TaskStatusNotification {

    @Schema(description = "Task id")
    private Long taskId;

    @Schema(description = "User id")
    private Long userId;

    @Schema(description = "Old status label")
    private String fromStatus;

    @Schema(description = "New status label")
    private String toStatus;

    @Schema(description = "Occurred at")
    private LocalDateTime occurredAt;

    @Schema(description = "Error message if failed/rejected")
    private String message;
}


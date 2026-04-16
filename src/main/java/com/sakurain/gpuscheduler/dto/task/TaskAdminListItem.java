package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Admin task list item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAdminListItem {

    @Schema(description = "Task id")
    private Long id;

    @Schema(description = "Task title")
    private String title;

    @Schema(description = "Task remark")
    private String description;

    @Schema(description = "Task type")
    private String taskType;

    @Schema(description = "Task status code")
    private Integer status;

    @Schema(description = "Task status label")
    private String statusLabel;
}

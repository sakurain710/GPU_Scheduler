package com.sakurain.gpuscheduler.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Structured dead-letter queue item")
public class DlqItemResponse {

    @Schema(description = "Task id")
    private Long taskId;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Retry count")
    private long retryCount;

    @Schema(description = "Failure reason")
    private String failureReason;

    @Schema(description = "Entered DLQ at")
    private LocalDateTime enteredDlqAt;
}

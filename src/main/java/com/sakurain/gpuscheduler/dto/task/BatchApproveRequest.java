package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量审批请求")
public class BatchApproveRequest {

    @NotEmpty(message = "taskIds cannot be empty")
    @Schema(description = "任务ID列表", example = "[101,102,103]")
    private List<@NotNull(message = "taskId cannot be null") @Positive(message = "taskId must be positive") Long> taskIds;

    @Schema(description = "拒绝原因（仅批量拒绝时使用）", example = "批量校验未通过")
    private String reason;
}

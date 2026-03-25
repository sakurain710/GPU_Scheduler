package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 提交GPU任务请求
 */
@Schema(description = "Submit GPU task request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitTaskRequest {

    @Schema(description = "Task title", example = "Train LLM")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称不能超过128个字符")
    private String title;

    @Schema(description = "Task description")
    @Size(max = 65535, message = "描述过长")
    private String description;

    @Schema(description = "Task type", example = "TRAINING")
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 64, message = "任务类型不能超过64个字符")
    private String taskType;

    @Schema(description = "Minimum required VRAM in GB", example = "16")
    @NotNull(message = "最低显存需求不能为空")
    @DecimalMin(value = "0.01", message = "最低显存需求必须大于0")
    private BigDecimal minMemoryGb;

    @Schema(description = "Estimated compute requirement in GFLOP", example = "15000")
    @NotNull(message = "计算量不能为空")
    @DecimalMin(value = "0.0001", message = "计算量必须大于0")
    private BigDecimal computeUnitsGflop;

    @Schema(description = "Base priority [1,10]", example = "5")
    @Min(value = 1, message = "优先级最小为1")
    @Max(value = 10, message = "优先级最大为10")
    private Integer basePriority = 5;
}

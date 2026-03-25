package com.sakurain.gpuscheduler.dto.gpu;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 注册GPU请求
 */
@Schema(description = "Register GPU request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterGpuRequest {

    @Schema(description = "GPU model name", example = "NVIDIA A100 80GB")
    @NotBlank(message = "GPU型号名称不能为空")
    @Size(max = 128, message = "名称不能超过128个字符")
    private String name;

    @Schema(description = "Manufacturer", example = "NVIDIA")
    @NotBlank(message = "制造商不能为空")
    @Size(max = 64, message = "制造商不能超过64个字符")
    private String manufacturer;

    @Schema(description = "Memory size in GB", example = "80")
    @NotNull(message = "显存大小不能为空")
    @DecimalMin(value = "0.01", message = "显存必须大于0")
    private BigDecimal memoryGb;

    @Schema(description = "Compute power in TFLOPS", example = "19.5")
    @NotNull(message = "算力不能为空")
    @DecimalMin(value = "0.0001", message = "算力必须大于0")
    private BigDecimal computingPowerTflops;

    @Schema(description = "Remark")
    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}

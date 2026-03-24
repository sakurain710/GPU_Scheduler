package com.sakurain.gpuscheduler.dto.gpu;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 注册GPU请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterGpuRequest {

    @NotBlank(message = "GPU型号名称不能为空")
    @Size(max = 128, message = "名称不能超过128个字符")
    private String name;

    @NotBlank(message = "制造商不能为空")
    @Size(max = 64, message = "制造商不能超过64个字符")
    private String manufacturer;

    @NotNull(message = "显存大小不能为空")
    @DecimalMin(value = "0.01", message = "显存必须大于0")
    private BigDecimal memoryGb;

    @NotNull(message = "算力不能为空")
    @DecimalMin(value = "0.0001", message = "算力必须大于0")
    private BigDecimal computingPowerTflops;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}

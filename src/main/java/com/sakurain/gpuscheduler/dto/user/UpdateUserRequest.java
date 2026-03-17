package com.sakurain.gpuscheduler.dto.user;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 更新用户请求 DTO
 */
@Data
public class UpdateUserRequest {

    @Email(message = "邮箱格式不正确")
    private String email;

    private Integer status;
}

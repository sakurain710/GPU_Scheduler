package com.sakurain.gpuscheduler.dto.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer roleType;
    private Integer status;
    private Integer sortOrder;
    private Long parentRoleId;
    private List<Long> permissionIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

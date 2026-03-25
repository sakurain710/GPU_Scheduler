package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.role.AssignPermissionsRequest;
import com.sakurain.gpuscheduler.dto.role.AssignUsersRequest;
import com.sakurain.gpuscheduler.dto.role.CreateRoleRequest;
import com.sakurain.gpuscheduler.dto.role.RoleResponse;
import com.sakurain.gpuscheduler.dto.role.UpdateRoleRequest;
import com.sakurain.gpuscheduler.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器
 */
@Slf4j
@Tag(name = "Role Management", description = "Role CRUD and role-user/permission bindings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAnyRole('ROLE_ADMIN')")
public class RoleController {

    private final RoleService roleService;

    @Autowired
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 创建角色
     */
    @Operation(summary = "Create role")
    @PostMapping
    public Result<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return Result.success(response);
    }

    /**
     * 更新角色
     */
    @Operation(summary = "Update role")
    @PutMapping("/{roleId}")
    public Result<RoleResponse> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = roleService.updateRole(roleId, request);
        return Result.success(response);
    }

    /**
     * 删除角色
     */
    @Operation(summary = "Delete role")
    @DeleteMapping("/{roleId}")
    public Result<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return Result.success(null);
    }

    /**
     * 获取角色详情
     */
    @Operation(summary = "Get role by id")
    @GetMapping("/{roleId}")
    public Result<RoleResponse> getRoleById(@PathVariable Long roleId) {
        RoleResponse response = roleService.getRoleById(roleId);
        return Result.success(response);
    }

    /**
     * 获取所有角色列表
     */
    @Operation(summary = "List all roles")
    @GetMapping
    public Result<List<RoleResponse>> listAllRoles() {
        List<RoleResponse> response = roleService.listAllRoles();
        return Result.success(response);
    }

    /**
     * 为角色分配权限
     */
    @Operation(summary = "Assign permissions to role")
    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody AssignPermissionsRequest request) {
        roleService.assignPermissions(roleId, request.getPermissionIds());
        return Result.success(null);
    }

    /**
     * 撤销角色的权限
     */
    @Operation(summary = "Revoke role permissions")
    @DeleteMapping("/{roleId}/permissions")
    public Result<Void> revokePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody AssignPermissionsRequest request) {
        roleService.revokePermissions(roleId, request.getPermissionIds());
        return Result.success(null);
    }

    /**
     * 为角色分配用户
     */
    @Operation(summary = "Assign users to role")
    @PostMapping("/{roleId}/users")
    public Result<Void> assignUsers(
            @PathVariable Long roleId,
            @Valid @RequestBody AssignUsersRequest request) {
        roleService.assignUsers(roleId, request.getUserIds(), request.getExpiresAt());
        return Result.success(null);
    }

    /**
     * 解除角色的用户绑定
     */
    @Operation(summary = "Unassign users from role")
    @DeleteMapping("/{roleId}/users")
    public Result<Void> unassignUsers(
            @PathVariable Long roleId,
            @Valid @RequestBody AssignUsersRequest request) {
        roleService.unassignUsers(roleId, request.getUserIds());
        return Result.success(null);
    }
}

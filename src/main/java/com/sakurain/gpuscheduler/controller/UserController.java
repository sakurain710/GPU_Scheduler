package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.user.CreateUserRequest;
import com.sakurain.gpuscheduler.dto.user.UpdateUserRequest;
import com.sakurain.gpuscheduler.dto.user.UserResponse;
import com.sakurain.gpuscheduler.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理控制器
 */
@Slf4j
@Tag(name = "User Management", description = "User CRUD and role assignment")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 创建用户
     */
    @Operation(summary = "Create user")
    @PostMapping
    public Result<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return Result.success(response);
    }

    /**
     * 更新用户
     */
    @Operation(summary = "Update user")
    @PutMapping("/{userId}")
    public Result<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(userId, request);
        return Result.success(response);
    }

    /**
     * 删除用户
     */
    @Operation(summary = "Delete user")
    @DeleteMapping("/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return Result.success(null);
    }

    /**
     * 获取用户详情
     */
    @Operation(summary = "Get user by id")
    @GetMapping("/{userId}")
    public Result<UserResponse> getUserById(@PathVariable Long userId) {
        UserResponse response = userService.getUserById(userId);
        return Result.success(response);
    }

    /**
     * 分页查询用户列表
     */
    @Operation(summary = "List users", description = "Supports pagination and query by username/email/status")
    @GetMapping
    public Result<IPage<UserResponse>> listUsers(
            @Parameter(description = "Page number, starts from 1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @Parameter(description = "User status") @RequestParam(required = false) Integer status) {
        IPage<UserResponse> response = userService.listUsers(page, size, username, email, status);
        return Result.success(response);
    }

    /**
     * 为用户分配角色
     */
    @Operation(summary = "Assign roles to user")
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(
            @PathVariable Long userId,
            @RequestBody List<Long> roleIds,
            @RequestParam(required = false) LocalDateTime expiresAt) {
        userService.assignRoles(userId, roleIds, expiresAt);
        return Result.success(null);
    }
}

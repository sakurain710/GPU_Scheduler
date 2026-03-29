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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * 用户管理
 */
@Slf4j
@Tag(name = "用户管理", description = "用户CRUD和角色分配")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping({"/api/users", "/api/v1/users"})
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
@Validated
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 创建用户。
     */
    @Operation(summary = "创建用户")
    @PostMapping
    public Result<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return Result.success(response);
    }

    /**
     * 更新用户。
     */
    @Operation(summary = "更新用户")
    @PutMapping("/{userId}")
    public Result<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(userId, request);
        return Result.success(response);
    }

    /**
     * 删除用户。
     */
    @Operation(summary = "删除用户")
    @DeleteMapping("/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return Result.success(null);
    }

    /**
     * 获取用户详情。
     */
    @Operation(summary = "根据ID获取用户")
    @GetMapping("/{userId}")
    public Result<UserResponse> getUserById(@PathVariable Long userId) {
        UserResponse response = userService.getUserById(userId);
        return Result.success(response);
    }

    /**
     * 用户分页列表，支持过滤和排序。
     */
    @Operation(summary = "列出用户", description = "支持分页、过滤和排序")
    @GetMapping
    public Result<IPage<UserResponse>> listUsers(
            @Parameter(description = "页码，从1开始")
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @Parameter(description = "用户状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "排序字段: createdAt/username/email/status/id")
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向: asc/desc")
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        IPage<UserResponse> response = userService.listUsers(page, size, username, email, status, sortBy, sortDir);
        return Result.success(response);
    }

    /**
     * 为用户分配角色。
     */
    @Operation(summary = "为用户分配角色")
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(
            @PathVariable Long userId,
            @RequestBody List<Long> roleIds,
            @RequestParam(required = false) LocalDateTime expiresAt) {
        userService.assignRoles(userId, roleIds, expiresAt);
        return Result.success(null);
    }
}

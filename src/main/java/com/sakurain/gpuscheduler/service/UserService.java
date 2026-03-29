package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.dto.user.CreateUserRequest;
import com.sakurain.gpuscheduler.dto.user.UpdateUserRequest;
import com.sakurain.gpuscheduler.dto.user.UserResponse;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.entity.UserRole;
import com.sakurain.gpuscheduler.exception.DuplicateResourceException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.mapper.UserRoleMapper;
import com.sakurain.gpuscheduler.util.PaginationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserMapper userMapper,
                       RoleMapper roleMapper,
                       UserRoleMapper userRoleMapper,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建用户
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("创建用户: username={}, email={}", request.getUsername(), request.getEmail());

        User existingUser = userMapper.selectByUsername(request.getUsername());
        if (existingUser != null) {
            throw new DuplicateResourceException("用户名已存在");
        }

        User existingEmail = userMapper.selectByEmail(request.getEmail());
        if (existingEmail != null) {
            throw new DuplicateResourceException("邮箱已被使用");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .status(request.getStatus())
                .build();

        userMapper.insert(user);
        return convertToResponse(user);
    }

    /**
     * 更新用户
     */
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            User existingEmail = userMapper.selectByEmail(request.getEmail());
            if (existingEmail != null && !existingEmail.getId().equals(userId)) {
                throw new DuplicateResourceException("邮箱已被使用");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        userMapper.updateById(user);
        return convertToResponse(user);
    }

    /**
     * 删除用户（软删除）
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        userMapper.deleteById(userId);
    }

    /**
     * 获取用户详情
     */
    public UserResponse getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return convertToResponse(user);
    }

    /**
     * 分页查询用户列表
     */
    public IPage<UserResponse> listUsers(Integer page,
                                         Integer size,
                                         String username,
                                         String email,
                                         Integer status,
                                         String sortBy,
                                         String sortDir) {
        Page<User> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 10, 200)
        );

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(username != null && !username.isBlank(), User::getUsername, username)
                .like(email != null && !email.isBlank(), User::getEmail, email)
                .eq(status != null, User::getStatus, status);
        applyUserSort(wrapper, sortBy, sortDir);

        IPage<User> userPage = userMapper.selectPage(pageParam, wrapper);
        return userPage.convert(this::convertToResponse);
    }

    /**
     * 为用户分配角色
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds, LocalDateTime expiresAt) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                Role role = roleMapper.selectById(roleId);
                if (role == null) {
                    throw new ResourceNotFoundException("角色不存在: " + roleId);
                }
            }
        }

        userRoleMapper.deleteByUserId(userId);

        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> UserRole.builder()
                            .userId(userId)
                            .roleId(roleId)
                            .expiresAt(expiresAt)
                            .build())
                    .collect(Collectors.toList());
            userRoleMapper.batchInsert(userRoles);
        }
    }

    private void applyUserSort(LambdaQueryWrapper<User> wrapper, String sortBy, String sortDir) {
        boolean asc = !"desc".equalsIgnoreCase(sortDir);
        String key = sortBy == null ? "createdAt" : sortBy;
        switch (key) {
            case "username" -> wrapper.orderBy(true, asc, User::getUsername);
            case "email" -> wrapper.orderBy(true, asc, User::getEmail);
            case "status" -> wrapper.orderBy(true, asc, User::getStatus);
            case "id" -> wrapper.orderBy(true, asc, User::getId);
            default -> wrapper.orderBy(true, asc, User::getCreatedAt);
        }
    }

    /**
     * 转换为响应DTO
     */
    private UserResponse convertToResponse(User user) {
        List<Role> roles = roleMapper.selectByUserId(user.getId());
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .roles(roleCodes)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

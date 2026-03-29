package com.sakurain.gpuscheduler.service;

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

        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(request.getUsername());
        if (existingUser != null) {
            log.warn("创建用户失败 - 用户名已存在: username={}", request.getUsername());
            throw new DuplicateResourceException("用户名已存在");
        }

        // 检查邮箱是否已存在
        User existingEmail = userMapper.selectByEmail(request.getEmail());
        if (existingEmail != null) {
            log.warn("创建用户失败 - 邮箱已被使用: email={}", request.getEmail());
            throw new DuplicateResourceException("邮箱已被使用");
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .status(request.getStatus())
                .build();

        userMapper.insert(user);

        log.info("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());

        return convertToResponse(user);
    }

    /**
     * 更新用户
     */
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        log.info("更新用户: userId={}, email={}, status={}", userId, request.getEmail(), request.getStatus());

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("更新用户失败 - 用户不存在: userId={}", userId);
            throw new ResourceNotFoundException("用户不存在");
        }

        // 更新邮箱
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            User existingEmail = userMapper.selectByEmail(request.getEmail());
            if (existingEmail != null && !existingEmail.getId().equals(userId)) {
                log.warn("更新用户失败 - 邮箱已被使用: email={}", request.getEmail());
                throw new DuplicateResourceException("邮箱已被使用");
            }
            user.setEmail(request.getEmail());
        }

        // 更新状态
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        userMapper.updateById(user);

        log.info("用户更新成功: userId={}, username={}", userId, user.getUsername());

        return convertToResponse(user);
    }

    /**
     * 删除用户（软删除）
     */
    @Transactional
    public void deleteUser(Long userId) {
        log.info("删除用户: userId={}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("删除用户失败 - 用户不存在: userId={}", userId);
            throw new ResourceNotFoundException("用户不存在");
        }

        // 软删除
        userMapper.deleteById(userId);

        log.info("用户删除成功: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 获取用户详情
     */
    public UserResponse getUserById(Long userId) {
        log.debug("获取用户详情: userId={}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("获取用户失败 - 用户不存在: userId={}", userId);
            throw new ResourceNotFoundException("用户不存在");
        }

        return convertToResponse(user);
    }

    /**
     * 分页查询用户列表
     */
    public IPage<UserResponse> listUsers(Integer page, Integer size, String username, String email, Integer status) {
        log.debug("分页查询用户列表: page={}, size={}, username={}, email={}, status={}",
                page, size, username, email, status);

        Page<User> pageParam = new Page<>(
                PaginationUtils.normalizePage(page),
                PaginationUtils.normalizeSize(size, 10, 200)
        );
        IPage<User> userPage = userMapper.selectPageWithFilter(pageParam, username, email, status);

        log.debug("用户列表查询成功: total={}, pages={}", userPage.getTotal(), userPage.getPages());

        return userPage.convert(this::convertToResponse);
    }

    /**
     * 为用户分配角色
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds, LocalDateTime expiresAt) {
        log.info("为用户分配角色: userId={}, roleIds={}, expiresAt={}", userId, roleIds, expiresAt);

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("分配角色失败 - 用户不存在: userId={}", userId);
            throw new ResourceNotFoundException("用户不存在");
        }

        // 验证角色是否存在
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                Role role = roleMapper.selectById(roleId);
                if (role == null) {
                    log.warn("分配角色失败 - 角色不存在: roleId={}", roleId);
                    throw new ResourceNotFoundException("角色不存在: " + roleId);
                }
            }
        }

        // 删除用户现有角色
        userRoleMapper.deleteByUserId(userId);
        log.debug("已删除用户现有角色: userId={}", userId);

        // 分配新角色
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> UserRole.builder()
                            .userId(userId)
                            .roleId(roleId)
                            .expiresAt(expiresAt)
                            .build())
                    .collect(Collectors.toList());

            userRoleMapper.batchInsert(userRoles);
            log.info("角色分配成功: userId={}, roleCount={}", userId, roleIds.size());
        } else {
            log.info("角色分配成功（清空所有角色）: userId={}", userId);
        }
    }

    /**
     * 转换为响应 DTO
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

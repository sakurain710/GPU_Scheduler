package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.role.CreateRoleRequest;
import com.sakurain.gpuscheduler.dto.role.RoleResponse;
import com.sakurain.gpuscheduler.dto.role.UpdateRoleRequest;
import com.sakurain.gpuscheduler.entity.Permission;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.RolePermission;
import com.sakurain.gpuscheduler.entity.UserRole;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.DuplicateResourceException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.mapper.PermissionMapper;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.RolePermissionMapper;
import com.sakurain.gpuscheduler.mapper.UserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务
 */
@Slf4j
@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    @Autowired
    public RoleService(RoleMapper roleMapper,
                       PermissionMapper permissionMapper,
                       RolePermissionMapper rolePermissionMapper,
                       UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 创建角色
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        log.info("创建角色: code={}, name={}", request.getCode(), request.getName());

        // 检查角色编码是否已存在
        Role existingRole = roleMapper.selectByCode(request.getCode());
        if (existingRole != null) {
            log.warn("创建角色失败 - 角色编码已存在: code={}", request.getCode());
            throw new DuplicateResourceException("角色编码已存在");
        }

        // 创建角色
        Role role = Role.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .roleType(request.getRoleType())
                .status(request.getStatus())
                .sortOrder(request.getSortOrder())
                .parentRoleId(request.getParentRoleId())
                .build();

        roleMapper.insert(role);

        // 分配权限
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            assignPermissions(role.getId(), request.getPermissionIds());
        }

        log.info("角色创建成功: roleId={}, code={}", role.getId(), role.getCode());

        return convertToResponse(role);
    }

    /**
     * 更新角色
     */
    @Transactional
    public RoleResponse updateRole(Long roleId, UpdateRoleRequest request) {
        log.info("更新角色: roleId={}", roleId);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("更新角色失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        // 更新角色信息
        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getRoleType() != null) {
            role.setRoleType(request.getRoleType());
        }
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            role.setSortOrder(request.getSortOrder());
        }
        if (request.getParentRoleId() != null) {
            role.setParentRoleId(request.getParentRoleId());
        }

        roleMapper.updateById(role);

        // 更新权限
        if (request.getPermissionIds() != null) {
            rolePermissionMapper.deleteByRoleId(roleId);
            if (!request.getPermissionIds().isEmpty()) {
                assignPermissions(roleId, request.getPermissionIds());
            }
            log.debug("角色权限已更新: roleId={}, permissionCount={}", roleId, request.getPermissionIds().size());
        }

        log.info("角色更新成功: roleId={}, code={}", roleId, role.getCode());

        return convertToResponse(role);
    }

    /**
     * 删除角色
     */
    @Transactional
    public void deleteRole(Long roleId) {
        log.info("删除角色: roleId={}", roleId);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("删除角色失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        // 检查是否有用户使用该角色
        List<UserRole> userRoles = userRoleMapper.selectByRoleId(roleId);
        if (!userRoles.isEmpty()) {
            log.warn("删除角色失败 - 该角色下还有用户: roleId={}, userCount={}", roleId, userRoles.size());
            throw new BusinessException("ROLE_IN_USE", "该角色下还有用户，无法删除", 400);
        }

        // 删除角色权限关联
        rolePermissionMapper.deleteByRoleId(roleId);
        log.debug("已删除角色权限关联: roleId={}", roleId);

        // 删除角色
        roleMapper.deleteById(roleId);

        log.info("角色删除成功: roleId={}, code={}", roleId, role.getCode());
    }

    /**
     * 获取角色详情
     */
    public RoleResponse getRoleById(Long roleId) {
        log.debug("获取角色详情: roleId={}", roleId);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("获取角色失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        return convertToResponse(role);
    }

    /**
     * 获取所有角色列表
     */
    public List<RoleResponse> listAllRoles() {
        log.debug("获取所有角色列表");

        List<Role> roles = roleMapper.selectList(null);

        log.debug("角色列表查询成功: count={}", roles.size());

        return roles.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 为角色分配权限
     */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        log.info("为角色分配权限: roleId={}, permissionIds={}", roleId, permissionIds);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("分配权限失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        // 验证权限是否存在
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                Permission permission = permissionMapper.selectById(permissionId);
                if (permission == null) {
                    log.warn("分配权限失败 - 权限不存在: permissionId={}", permissionId);
                    throw new ResourceNotFoundException("权限不存在: " + permissionId);
                }
            }
        }

        // 删除现有权限
        rolePermissionMapper.deleteByRoleId(roleId);
        log.debug("已删除角色现有权限: roleId={}", roleId);

        // 分配新权限
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> rolePermissions = permissionIds.stream()
                    .map(permissionId -> RolePermission.builder()
                            .roleId(roleId)
                            .permissionId(permissionId)
                            .build())
                    .collect(Collectors.toList());

            rolePermissionMapper.batchInsert(rolePermissions);
            log.info("权限分配成功: roleId={}, permissionCount={}", roleId, permissionIds.size());
        } else {
            log.info("权限分配成功（清空所有权限）: roleId={}", roleId);
        }
    }

    /**
     * 撤销角色的权限
     */
    @Transactional
    public void revokePermissions(Long roleId, List<Long> permissionIds) {
        log.info("撤销角色权限: roleId={}, permissionIds={}", roleId, permissionIds);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("撤销权限失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                rolePermissionMapper.deleteByRoleIdAndPermissionId(roleId, permissionId);
            }
            log.info("权限撤销成功: roleId={}, permissionCount={}", roleId, permissionIds.size());
        } else {
            log.info("权限撤销成功（无权限需要撤销）: roleId={}", roleId);
        }
    }

    /**
     * 为角色分配用户
     */
    @Transactional
    public void assignUsers(Long roleId, List<Long> userIds, LocalDateTime expiresAt) {
        log.info("为角色分配用户: roleId={}, userIds={}, expiresAt={}", roleId, userIds, expiresAt);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("分配用户失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        if (userIds != null && !userIds.isEmpty()) {
            List<UserRole> userRoles = userIds.stream()
                    .map(userId -> UserRole.builder()
                            .userId(userId)
                            .roleId(roleId)
                            .expiresAt(expiresAt)
                            .build())
                    .collect(Collectors.toList());

            userRoleMapper.batchInsert(userRoles);
            log.info("用户分配成功: roleId={}, userCount={}", roleId, userIds.size());
        } else {
            log.info("用户分配成功（无用户需要分配）: roleId={}", roleId);
        }
    }

    /**
     * 解除角色的用户绑定
     */
    @Transactional
    public void unassignUsers(Long roleId, List<Long> userIds) {
        log.info("解除角色用户绑定: roleId={}, userIds={}", roleId, userIds);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("解除用户绑定失败 - 角色不存在: roleId={}", roleId);
            throw new ResourceNotFoundException("角色不存在");
        }

        if (userIds != null && !userIds.isEmpty()) {
            for (Long userId : userIds) {
                userRoleMapper.deleteByUserIdAndRoleId(userId, roleId);
            }
            log.info("用户解绑成功: roleId={}, userCount={}", roleId, userIds.size());
        } else {
            log.info("用户解绑成功（无用户需要解绑）: roleId={}", roleId);
        }
    }

    /**
     * 转换为响应 DTO
     */
    private RoleResponse convertToResponse(Role role) {
        List<Permission> permissions = permissionMapper.selectByRoleId(role.getId());
        List<Long> permissionIds = permissions.stream()
                .map(Permission::getId)
                .collect(Collectors.toList());

        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .roleType(role.getRoleType())
                .status(role.getStatus())
                .sortOrder(role.getSortOrder())
                .parentRoleId(role.getParentRoleId())
                .permissionIds(permissionIds)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}

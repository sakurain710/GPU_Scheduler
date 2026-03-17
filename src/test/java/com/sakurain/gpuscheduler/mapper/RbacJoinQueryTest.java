package com.sakurain.gpuscheduler.mapper;

import com.sakurain.gpuscheduler.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 核心关联查询测试
 * 验证 用户->角色->权限 链路的连接查询和权限过滤
 */
@SpringBootTest
class RbacJoinQueryTest {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private ResourceMapper resourceMapper;
    @Autowired
    private RolePermissionMapper rolePermissionMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;

    private User testUser;
    private Role adminRole;
    private Role viewerRole;
    private Resource testResource;
    private Permission viewPermission;
    private Permission editPermission;
    // 每次测试使用唯一后缀，避免唯一键冲突
    private String s;

    @BeforeEach
    void setUp() {
        s = String.valueOf(System.nanoTime());

        testResource = Resource.builder()
                .code("res:" + s).name("用户列表").type(2).sortOrder(1).status(1).build();
        resourceMapper.insert(testResource);

        viewPermission = Permission.builder()
                .code("perm:view:" + s).name("查看").resourceId(testResource.getId())
                .action("view").status(1).build();
        permissionMapper.insert(viewPermission);

        editPermission = Permission.builder()
                .code("perm:edit:" + s).name("编辑").resourceId(testResource.getId())
                .action("edit").status(1).build();
        permissionMapper.insert(editPermission);

        adminRole = Role.builder()
                .code("ROLE_ADMIN_" + s).name("管理员").roleType(1).sortOrder(1).status(1).build();
        roleMapper.insert(adminRole);

        viewerRole = Role.builder()
                .code("ROLE_VIEWER_" + s).name("只读").roleType(2).sortOrder(2).status(1).build();
        roleMapper.insert(viewerRole);

        // admin: view + edit; viewer: view only
        rolePermissionMapper.insert(RolePermission.builder()
                .roleId(adminRole.getId()).permissionId(viewPermission.getId()).build());
        rolePermissionMapper.insert(RolePermission.builder()
                .roleId(adminRole.getId()).permissionId(editPermission.getId()).build());
        rolePermissionMapper.insert(RolePermission.builder()
                .roleId(viewerRole.getId()).permissionId(viewPermission.getId()).build());

        testUser = User.builder()
                .username("user_" + s).password("$2a$10$x").gender(0).userType(1).status(1).build();
        userMapper.insert(testUser);

        userRoleMapper.insert(UserRole.builder()
                .userId(testUser.getId()).roleId(adminRole.getId()).build());
    }

    @Test
    void testSelectRolesByUserId() {
        List<Role> roles = roleMapper.selectByUserId(testUser.getId());
        assertEquals(1, roles.size());
        assertEquals(adminRole.getCode(), roles.get(0).getCode());
    }

    @Test
    void testSelectPermissionsByRoleId() {
        List<Permission> adminPerms = permissionMapper.selectByRoleId(adminRole.getId());
        assertEquals(2, adminPerms.size());

        List<Permission> viewerPerms = permissionMapper.selectByRoleId(viewerRole.getId());
        assertEquals(1, viewerPerms.size());
        assertEquals(viewPermission.getCode(), viewerPerms.get(0).getCode());
    }

    @Test
    void testSelectPermissionsByUserId() {
        List<Permission> permissions = permissionMapper.selectByUserId(testUser.getId());
        assertEquals(2, permissions.size());

        List<String> codes = permissions.stream().map(Permission::getCode).toList();
        assertTrue(codes.contains(viewPermission.getCode()));
        assertTrue(codes.contains(editPermission.getCode()));
    }

    @Test
    void testSelectPermissionsByRoleIds() {
        // admin + viewer 的权限去重后仍是2条
        List<Long> roleIds = Arrays.asList(adminRole.getId(), viewerRole.getId());
        List<Permission> permissions = permissionMapper.selectByRoleIds(roleIds);
        assertEquals(2, permissions.size());
    }

    @Test
    void testSelectResourcesByPermissionIds() {
        List<Long> permIds = Arrays.asList(viewPermission.getId(), editPermission.getId());
        List<Resource> resources = resourceMapper.selectByPermissionIds(permIds);
        assertEquals(1, resources.size());
        assertEquals(testResource.getCode(), resources.get(0).getCode());
    }

    @Test
    void testUserWithNoRoleHasNoPermissions() {
        User noRoleUser = User.builder()
                .username("norole_" + s).password("$2a$10$x").gender(0).userType(1).status(1).build();
        userMapper.insert(noRoleUser);

        List<Permission> permissions = permissionMapper.selectByUserId(noRoleUser.getId());
        assertTrue(permissions.isEmpty());
    }

    @Test
    void testExpiredUserRoleExcludedFromPermissions() {
        User expiredUser = User.builder()
                .username("expired_" + s).password("$2a$10$x").gender(0).userType(1).status(1).build();
        userMapper.insert(expiredUser);

        userRoleMapper.insert(UserRole.builder()
                .userId(expiredUser.getId())
                .roleId(adminRole.getId())
                .expiresAt(java.time.LocalDateTime.now().minusDays(1))
                .build());

        List<Permission> permissions = permissionMapper.selectByUserId(expiredUser.getId());
        assertTrue(permissions.isEmpty(), "过期角色不应返回权限");
    }

    @Test
    void testBatchInsertRolePermissions() {
        rolePermissionMapper.deleteByRoleId(adminRole.getId());

        List<RolePermission> list = Arrays.asList(
                RolePermission.builder().roleId(adminRole.getId()).permissionId(viewPermission.getId()).build(),
                RolePermission.builder().roleId(adminRole.getId()).permissionId(editPermission.getId()).build()
        );
        int count = rolePermissionMapper.batchInsert(list);
        assertEquals(2, count);

        List<Permission> permissions = permissionMapper.selectByRoleId(adminRole.getId());
        assertEquals(2, permissions.size());
    }

    @Test
    void testRoleHierarchy() {
        Role childRole = Role.builder()
                .code("ROLE_CHILD_" + s).name("子角色")
                .parentRoleId(adminRole.getId()).roleType(2).sortOrder(3).status(1).build();
        roleMapper.insert(childRole);

        List<Role> hierarchy = roleMapper.selectRoleHierarchy(childRole.getId());
        assertEquals(2, hierarchy.size());

        List<String> codes = hierarchy.stream().map(Role::getCode).toList();
        assertTrue(codes.contains(childRole.getCode()));
        assertTrue(codes.contains(adminRole.getCode()));
    }
}

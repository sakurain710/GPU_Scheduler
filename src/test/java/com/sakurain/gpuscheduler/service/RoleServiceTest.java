package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.role.CreateRoleRequest;
import com.sakurain.gpuscheduler.dto.role.UpdateRoleRequest;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.mapper.PermissionMapper;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.RolePermissionMapper;
import com.sakurain.gpuscheduler.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private RoleService roleService;

    @Test
    void createRole_shouldRejectInvalidRoleType() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setCode("ROLE_X");
        request.setName("Role X");
        request.setRoleType(9);

        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.createRole(request));

        assertEquals("ROLE_TYPE_INVALID", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void updateRole_shouldRejectInvalidRoleType() {
        Role role = Role.builder().id(1L).code("ROLE_A").name("A").roleType(1).status(1).sortOrder(0).build();
        when(roleMapper.selectById(1L)).thenReturn(role);

        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRoleType(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.updateRole(1L, request));

        assertEquals("ROLE_TYPE_INVALID", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
    }
}

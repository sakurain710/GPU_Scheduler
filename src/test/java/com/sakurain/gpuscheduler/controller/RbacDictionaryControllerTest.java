package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.rbac.MenuNodeResponse;
import com.sakurain.gpuscheduler.entity.Permission;
import com.sakurain.gpuscheduler.entity.Resource;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.RbacDictionaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacDictionaryControllerTest {

    @Mock
    private RbacDictionaryService rbacDictionaryService;

    @Test
    void listPermissions_shouldReturnSuccessResult() {
        RbacDictionaryController controller = new RbacDictionaryController(rbacDictionaryService);
        List<Permission> permissions = List.of(Permission.builder().id(1L).code("task:approve").build());
        when(rbacDictionaryService.listPermissions(10L, 1)).thenReturn(permissions);

        Result<List<Permission>> result = controller.listPermissions(10L, 1);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("task:approve", result.getData().get(0).getCode());
    }

    @Test
    void listResources_shouldReturnSuccessResult() {
        RbacDictionaryController controller = new RbacDictionaryController(rbacDictionaryService);
        List<Resource> resources = List.of(Resource.builder().id(2L).code("task").build());
        when(rbacDictionaryService.listResources(null, 2, 1)).thenReturn(resources);

        Result<List<Resource>> result = controller.listResources(null, 2, 1);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("task", result.getData().get(0).getCode());
    }

    @Test
    void getMyMenuTree_shouldReturnSuccessResult() {
        RbacDictionaryController controller = new RbacDictionaryController(rbacDictionaryService);
        CustomUserDetails userDetails = buildUserDetails(88L);
        List<MenuNodeResponse> menuTree = List.of(
                MenuNodeResponse.builder().id(1L).code("menu:task").name("任务中心").build()
        );
        when(rbacDictionaryService.getCurrentUserMenuTree(88L)).thenReturn(menuTree);

        Result<List<MenuNodeResponse>> result = controller.getMyMenuTree(userDetails);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("menu:task", result.getData().get(0).getCode());
    }

    @Test
    void getMyButtonPermissions_shouldReturnSuccessResult() {
        RbacDictionaryController controller = new RbacDictionaryController(rbacDictionaryService);
        CustomUserDetails userDetails = buildUserDetails(99L);
        List<String> buttonCodes = List.of("task:approval:review", "ops:manage");
        when(rbacDictionaryService.getCurrentUserButtonPermissionCodes(99L)).thenReturn(buttonCodes);

        Result<List<String>> result = controller.getMyButtonPermissions(userDetails);

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals("task:approval:review", result.getData().get(0));
    }

    private CustomUserDetails buildUserDetails(Long userId) {
        User user = User.builder()
                .id(userId)
                .username("u" + userId)
                .password("x")
                .status(1)
                .build();
        Role role = Role.builder().id(1L).code("ROLE_USER").name("User").build();
        return new CustomUserDetails(user, List.of(role), List.of());
    }
}

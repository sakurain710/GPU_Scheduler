package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.rbac.MenuNodeResponse;
import com.sakurain.gpuscheduler.entity.Permission;
import com.sakurain.gpuscheduler.entity.Resource;
import com.sakurain.gpuscheduler.mapper.PermissionMapper;
import com.sakurain.gpuscheduler.mapper.ResourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacDictionaryServiceTest {

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private RbacDictionaryService rbacDictionaryService;

    @Test
    void getCurrentUserMenuTree_shouldBuildTreeByAncestorMenus() {
        Permission apiPermission = Permission.builder().id(11L).resourceId(300L).code("task:approval:read").build();
        Permission buttonPermission = Permission.builder().id(12L).resourceId(400L).code("task:approval:review").build();
        when(permissionMapper.selectByUserId(7L)).thenReturn(List.of(apiPermission, buttonPermission));

        Resource apiResource = Resource.builder()
                .id(300L).code("api:task:pending").type(2).status(1).parentId(200L).sortOrder(10).build();
        Resource buttonResource = Resource.builder()
                .id(400L).code("btn:task:review").type(3).status(1).parentId(200L).sortOrder(20).build();
        when(resourceMapper.selectBatchIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(apiResource, buttonResource));

        Resource menuTask = Resource.builder()
                .id(200L).code("menu:task").name("任务中心").path("/tasks").type(1).status(1).parentId(100L).sortOrder(20).build();
        Resource menuRoot = Resource.builder()
                .id(100L).code("menu:root").name("首页").path("/").type(1).status(1).parentId(null).sortOrder(10).build();
        when(resourceMapper.selectById(200L)).thenReturn(menuTask);
        when(resourceMapper.selectById(100L)).thenReturn(menuRoot);

        List<MenuNodeResponse> tree = rbacDictionaryService.getCurrentUserMenuTree(7L);

        assertEquals(1, tree.size());
        assertEquals("menu:root", tree.get(0).getCode());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("menu:task", tree.get(0).getChildren().get(0).getCode());
    }

    @Test
    void getCurrentUserButtonPermissionCodes_shouldReturnDistinctSortedCodes() {
        when(permissionMapper.selectByUserIdAndResourceType(7L, 3)).thenReturn(List.of(
                Permission.builder().code("ops:manage").build(),
                Permission.builder().code("task:approval:review").build(),
                Permission.builder().code("ops:manage").build()
        ));

        List<String> codes = rbacDictionaryService.getCurrentUserButtonPermissionCodes(7L);

        assertEquals(2, codes.size());
        assertEquals("ops:manage", codes.get(0));
        assertEquals("task:approval:review", codes.get(1));
    }

    @Test
    void getCurrentUserMenuTree_shouldReturnEmptyWhenNoPermission() {
        when(permissionMapper.selectByUserId(7L)).thenReturn(List.of());

        List<MenuNodeResponse> tree = rbacDictionaryService.getCurrentUserMenuTree(7L);

        assertTrue(tree.isEmpty());
    }
}

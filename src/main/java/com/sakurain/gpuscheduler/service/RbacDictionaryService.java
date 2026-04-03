package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.dto.rbac.MenuNodeResponse;
import com.sakurain.gpuscheduler.entity.Permission;
import com.sakurain.gpuscheduler.entity.Resource;
import com.sakurain.gpuscheduler.mapper.PermissionMapper;
import com.sakurain.gpuscheduler.mapper.ResourceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RbacDictionaryService {

    private static final int STATUS_ACTIVE = 1;
    private static final int RESOURCE_TYPE_MENU = 1;
    private static final int RESOURCE_TYPE_BUTTON = 3;

    private final PermissionMapper permissionMapper;
    private final ResourceMapper resourceMapper;

    public RbacDictionaryService(PermissionMapper permissionMapper, ResourceMapper resourceMapper) {
        this.permissionMapper = permissionMapper;
        this.resourceMapper = resourceMapper;
    }

    public List<Permission> listPermissions(Long resourceId, Integer status) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<Permission>()
                .eq(resourceId != null, Permission::getResourceId, resourceId)
                .eq(status != null, Permission::getStatus, status)
                .orderByAsc(Permission::getId);
        return permissionMapper.selectList(wrapper);
    }

    public List<Resource> listResources(Long parentId, Integer type, Integer status) {
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<Resource>()
                .eq(parentId != null, Resource::getParentId, parentId)
                .eq(type != null, Resource::getType, type)
                .eq(status != null, Resource::getStatus, status)
                .orderByAsc(Resource::getSortOrder)
                .orderByAsc(Resource::getId);
        return resourceMapper.selectList(wrapper);
    }

    public List<MenuNodeResponse> getCurrentUserMenuTree(Long userId) {
        if (userId == null) {
            return List.of();
        }

        List<Permission> permissions = permissionMapper.selectByUserId(userId);
        Set<Long> grantedResourceIds = permissions.stream()
                .map(Permission::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (grantedResourceIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Resource> resourceCache = resourceMapper.selectBatchIds(grantedResourceIds).stream()
                .filter(this::isActiveResource)
                .collect(Collectors.toMap(Resource::getId, item -> item, (left, right) -> left, HashMap::new));

        Set<Long> visibleMenuIds = new HashSet<>();
        for (Long resourceId : grantedResourceIds) {
            collectAncestorMenus(resourceId, resourceCache, visibleMenuIds);
        }

        if (visibleMenuIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MenuNodeResponse> nodeMap = new HashMap<>();
        for (Long menuId : visibleMenuIds) {
            Resource menu = resourceCache.get(menuId);
            if (menu == null || !isMenuResource(menu)) {
                continue;
            }
            nodeMap.put(menuId, toMenuNode(menu));
        }

        List<MenuNodeResponse> roots = new ArrayList<>();
        for (MenuNodeResponse node : nodeMap.values()) {
            if (node.getParentId() != null && nodeMap.containsKey(node.getParentId())) {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        sortTree(roots);
        return roots;
    }

    public List<String> getCurrentUserButtonPermissionCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }

        return permissionMapper.selectByUserIdAndResourceType(userId, RESOURCE_TYPE_BUTTON).stream()
                .map(Permission::getCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private void collectAncestorMenus(Long resourceId,
                                      Map<Long, Resource> resourceCache,
                                      Set<Long> visibleMenuIds) {
        Resource current = getOrLoadResource(resourceId, resourceCache);
        while (current != null && isActiveResource(current)) {
            if (isMenuResource(current)) {
                visibleMenuIds.add(current.getId());
            }
            if (current.getParentId() == null) {
                return;
            }
            current = getOrLoadResource(current.getParentId(), resourceCache);
        }
    }

    private Resource getOrLoadResource(Long resourceId, Map<Long, Resource> resourceCache) {
        if (resourceId == null) {
            return null;
        }
        Resource cached = resourceCache.get(resourceId);
        if (cached != null) {
            return cached;
        }
        Resource loaded = resourceMapper.selectById(resourceId);
        if (loaded != null && isActiveResource(loaded)) {
            resourceCache.put(resourceId, loaded);
            return loaded;
        }
        return null;
    }

    private boolean isActiveResource(Resource resource) {
        return resource != null && Integer.valueOf(STATUS_ACTIVE).equals(resource.getStatus());
    }

    private boolean isMenuResource(Resource resource) {
        return isActiveResource(resource) && Integer.valueOf(RESOURCE_TYPE_MENU).equals(resource.getType());
    }

    private MenuNodeResponse toMenuNode(Resource resource) {
        return MenuNodeResponse.builder()
                .id(resource.getId())
                .code(resource.getCode())
                .name(resource.getName())
                .path(resource.getPath())
                .parentId(resource.getParentId())
                .sortOrder(resource.getSortOrder())
                .build();
    }

    private void sortTree(List<MenuNodeResponse> nodes) {
        nodes.sort(Comparator.comparing(MenuNodeResponse::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(MenuNodeResponse::getId));
        for (MenuNodeResponse node : nodes) {
            if (!node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
    }
}

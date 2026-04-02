package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.entity.Permission;
import com.sakurain.gpuscheduler.entity.Resource;
import com.sakurain.gpuscheduler.mapper.PermissionMapper;
import com.sakurain.gpuscheduler.mapper.ResourceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RbacDictionaryService {

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
}

package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.entity.Permission;
import com.sakurain.gpuscheduler.entity.Resource;
import com.sakurain.gpuscheduler.service.RbacDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "RBAC 字典", description = "权限与资源字典查询")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/rbac")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public class RbacDictionaryController {

    private final RbacDictionaryService rbacDictionaryService;

    public RbacDictionaryController(RbacDictionaryService rbacDictionaryService) {
        this.rbacDictionaryService = rbacDictionaryService;
    }

    @Operation(summary = "查询权限字典")
    @GetMapping("/permissions")
    public Result<List<Permission>> listPermissions(
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false, defaultValue = "1") Integer status) {
        return Result.success(rbacDictionaryService.listPermissions(resourceId, status));
    }

    @Operation(summary = "查询资源字典")
    @GetMapping("/resources")
    public Result<List<Resource>> listResources(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false, defaultValue = "1") Integer status) {
        return Result.success(rbacDictionaryService.listResources(parentId, type, status));
    }
}

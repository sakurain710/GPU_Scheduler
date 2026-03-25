package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.gpu.GpuResponse;
import com.sakurain.gpuscheduler.dto.gpu.RegisterGpuRequest;
import com.sakurain.gpuscheduler.dto.gpu.UpdateGpuStatusRequest;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GPU资源管理控制器
 * <p>
 * 路由：
 *   GET    /api/gpu          — 列出所有GPU（所有认证用户）
 *   GET    /api/gpu/{id}     — GPU详情（所有认证用户）
 *   POST   /api/gpu          — 注册GPU（仅ADMIN）
 *   PUT    /api/gpu/{id}/status — 更新GPU状态（仅ADMIN）
 *   DELETE /api/gpu/{id}     — 删除GPU（仅ADMIN）
 *   GET    /api/gpu/health   — 健康检查（仅ADMIN）
 *   GET    /api/gpu/metrics  — 利用率统计（仅ADMIN）
 */
@Slf4j
@Tag(name = "GPU Management", description = "GPU registration, query, status update and metrics")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/gpu")
public class GpuController {

    private final GpuService gpuService;

    public GpuController(GpuService gpuService) {
        this.gpuService = gpuService;
    }

    /**
     * 列出所有GPU，支持按状态过滤和分页
     */
    @Operation(summary = "List GPUs", description = "Supports pagination and optional status filter")
    @GetMapping
    public Result<IPage<GpuResponse>> listGpus(
            @Parameter(description = "Page number, starts from 1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "GPU status: 1=IDLE,2=BUSY,3=OFFLINE,4=MAINTENANCE")
            @RequestParam(required = false) Integer status) {
        return Result.success(gpuService.listGpus(page, size, status));
    }

    /**
     * 查询GPU详情
     */
    @Operation(summary = "Get GPU by id")
    @GetMapping("/{gpuId}")
    public Result<GpuResponse> getGpu(@PathVariable Long gpuId) {
        return Result.success(gpuService.getGpu(gpuId));
    }

    /**
     * 注册新GPU（仅ADMIN）
     */
    @Operation(summary = "Register GPU", description = "Admin only")
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<GpuResponse> registerGpu(@Valid @RequestBody RegisterGpuRequest request) {
        Long operatorId = getCurrentUserId();
        GpuResponse response = gpuService.registerGpu(request, operatorId);
        return Result.success("GPU注册成功", response);
    }

    /**
     * 更新GPU状态（仅ADMIN）
     */
    @Operation(summary = "Update GPU status", description = "Admin only")
    @PutMapping("/{gpuId}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<GpuResponse> updateStatus(
            @PathVariable Long gpuId,
            @Valid @RequestBody UpdateGpuStatusRequest request) {
        GpuResponse response = gpuService.updateStatus(gpuId, request.getStatus());
        return Result.success("GPU状态更新成功", response);
    }

    /**
     * 删除GPU（仅ADMIN）
     */
    @Operation(summary = "Delete GPU", description = "Admin only")
    @DeleteMapping("/{gpuId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Void> deleteGpu(@PathVariable Long gpuId) {
        gpuService.deleteGpu(gpuId);
        return Result.success();
    }

    /**
     * GPU健康检查 — 各状态数量统计（仅ADMIN）
     */
    @Operation(summary = "GPU health summary", description = "Admin only")
    @GetMapping("/health")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Map<String, Long>> healthCheck() {
        return Result.success(gpuService.healthCheck());
    }

    /**
     * GPU利用率统计（仅ADMIN）
     */
    @Operation(summary = "GPU utilization metrics", description = "Admin only")
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Map<String, Object>> utilizationMetrics() {
        return Result.success(gpuService.utilizationMetrics());
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }
}

package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.gpu.GpuResponse;
import com.sakurain.gpuscheduler.dto.gpu.RegisterGpuRequest;
import com.sakurain.gpuscheduler.dto.gpu.UpdateGpuStatusRequest;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping
    public Result<IPage<GpuResponse>> listGpus(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer status) {
        return Result.success(gpuService.listGpus(page, size, status));
    }

    /**
     * 查询GPU详情
     */
    @GetMapping("/{gpuId}")
    public Result<GpuResponse> getGpu(@PathVariable Long gpuId) {
        return Result.success(gpuService.getGpu(gpuId));
    }

    /**
     * 注册新GPU（仅ADMIN）
     */
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
    @DeleteMapping("/{gpuId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Void> deleteGpu(@PathVariable Long gpuId) {
        gpuService.deleteGpu(gpuId);
        return Result.success();
    }

    /**
     * GPU健康检查 — 各状态数量统计（仅ADMIN）
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result<Map<String, Long>> healthCheck() {
        return Result.success(gpuService.healthCheck());
    }

    /**
     * GPU利用率统计（仅ADMIN）
     */
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

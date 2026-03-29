package com.sakurain.gpuscheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.gpu.GpuResponse;
import com.sakurain.gpuscheduler.dto.gpu.RegisterGpuRequest;
import com.sakurain.gpuscheduler.dto.gpu.UpdateGpuStatusRequest;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuService;
import com.sakurain.gpuscheduler.service.WorkerHeartbeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
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
 * GPU资源管理
 */
@Slf4j
@Tag(name = "GPU资源管理", description = "GPU注册、查询、状态更新和指标")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping({"/api/gpu", "/api/v1/gpu"})
@Validated
public class GpuController {

    private final GpuService gpuService;
    private final WorkerHeartbeatService workerHeartbeatService;

    public GpuController(GpuService gpuService, WorkerHeartbeatService workerHeartbeatService) {
        this.gpuService = gpuService;
        this.workerHeartbeatService = workerHeartbeatService;
    }

    /**
     * 列出GPU，支持按状态过滤和分页
     */
    @Operation(summary = "列出GPU", description = "支持分页和可选状态过滤")
    @GetMapping
    public Result<IPage<GpuResponse>> listGpus(
            @Parameter(description = "Page number, starts from 1")
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) Integer size,
            @Parameter(description = "GPU status: 1=IDLE,2=BUSY,3=OFFLINE,4=MAINTENANCE")
            @RequestParam(required = false) Integer status) {
        return Result.success(gpuService.listGpus(page, size, status));
    }

    /**
     * 查询GPU详情
     */
    @Operation(summary = "根据ID获取GPU")
    @GetMapping("/{gpuId}")
    public Result<GpuResponse> getGpu(@PathVariable Long gpuId) {
        return Result.success(gpuService.getGpu(gpuId));
    }

    /**
     * 注册新GPU（仅ADMIN）
     */
    @Operation(summary = "注册GPU", description = "仅管理员")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<GpuResponse> registerGpu(@Valid @RequestBody RegisterGpuRequest request) {
        Long operatorId = getCurrentUserId();
        GpuResponse response = gpuService.registerGpu(request, operatorId);
        return Result.success("GPU注册成功", response);
    }

    /**
     * 更新GPU状态（仅ADMIN）
     */
    @Operation(summary = "更新GPU状态", description = "仅管理员")
    @PutMapping("/{gpuId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<GpuResponse> updateStatus(
            @PathVariable Long gpuId,
            @Valid @RequestBody UpdateGpuStatusRequest request) {
        GpuResponse response = gpuService.updateStatus(gpuId, request.getStatus());
        return Result.success("GPU状态更新成功", response);
    }

    /**
     * 删除GPU（仅ADMIN）
     */
    @Operation(summary = "删除GPU", description = "仅管理员")
    @DeleteMapping("/{gpuId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteGpu(@PathVariable Long gpuId) {
        gpuService.deleteGpu(gpuId);
        return Result.success();
    }

    /**
     * GPU健康检查（仅ADMIN）
     */
    @Operation(summary = "GPU健康摘要", description = "仅管理员")
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Map<String, Long>> healthCheck() {
        return Result.success(gpuService.healthCheck());
    }

    /**
     * GPU利用率统计（仅ADMIN）
     */
    @Operation(summary = "GPU利用率指标", description = "仅管理员")
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Map<String, Object>> utilizationMetrics() {
        return Result.success(gpuService.utilizationMetrics());
    }

    /**
     * 上报GPU worker心跳（仅管理员）
     */
    @Operation(summary = "上报GPU worker心跳", description = "仅管理员")
    @PostMapping("/{gpuId}/heartbeat")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public Result<Map<String, Object>> heartbeat(@PathVariable Long gpuId) {
        workerHeartbeatService.beat(gpuId);
        return Result.success(Map.of(
                "gpuId", gpuId,
                "accepted", true,
                "heartbeatAgeSeconds", 0
        ));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }
}

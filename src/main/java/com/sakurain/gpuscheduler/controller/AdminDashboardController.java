package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardInitialResponse;
import com.sakurain.gpuscheduler.dto.dashboard.DlqListResponse;
import com.sakurain.gpuscheduler.dto.dashboard.QueueWaitTrendResponse;
import com.sakurain.gpuscheduler.service.AdminDashboardPushService;
import com.sakurain.gpuscheduler.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "管理员监控大屏", description = "管理员监控大屏专用接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/dashboard")
@Validated
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminDashboardPushService adminDashboardPushService;

    public AdminDashboardController(AdminDashboardService adminDashboardService,
                                    AdminDashboardPushService adminDashboardPushService) {
        this.adminDashboardService = adminDashboardService;
        this.adminDashboardPushService = adminDashboardPushService;
    }

    @Operation(summary = "获取管理员大屏首屏快照")
    @GetMapping("/initial")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','monitoring:read')")
    public Result<AdminDashboardInitialResponse> initial() {
        return Result.success(adminDashboardService.getInitialSnapshot(adminDashboardPushService.getLastPushAt()));
    }

    @Operation(summary = "获取排队耗时趋势")
    @GetMapping("/queue-wait-trend")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','monitoring:read')")
    public Result<QueueWaitTrendResponse> queueWaitTrend(
            @RequestParam(defaultValue = "DAY") String mode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate anchorDate) {
        return Result.success(adminDashboardService.getQueueWaitTrend(mode, anchorDate));
    }

    @Operation(summary = "获取结构化死信列表")
    @GetMapping("/dlq")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','monitoring:read')")
    public Result<DlqListResponse> dlq(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) Integer size) {
        return Result.success(adminDashboardService.listDlq(page, size));
    }
}

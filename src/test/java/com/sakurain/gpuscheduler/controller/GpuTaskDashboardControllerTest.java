package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.task.TaskDashboardResponse;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.TaskDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpuTaskDashboardControllerTest {

    @Mock private GpuTaskService gpuTaskService;
    @Mock private TaskDashboardService taskDashboardService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDashboard_shouldUseCurrentUserAndReturnPayload() {
        GpuTaskController controller = new GpuTaskController(gpuTaskService, taskDashboardService);
        TaskDashboardResponse response = TaskDashboardResponse.builder().build();
        when(taskDashboardService.getDashboard(99L, 1, 10, null, "updatedAt", "desc")).thenReturn(response);
        mockCurrentUser(99L);

        var result = controller.getDashboard(1, 10, null, "updatedAt", "desc");

        assertThat(result.getData()).isSameAs(response);
        verify(taskDashboardService).getDashboard(99L, 1, 10, null, "updatedAt", "desc");
    }

    private void mockCurrentUser(Long userId) {
        User user = User.builder()
                .id(userId)
                .username("alice")
                .password("pwd")
                .status(1)
                .build();
        Role role = Role.builder().code("ROLE_USER").build();
        CustomUserDetails userDetails = new CustomUserDetails(user, List.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }
}

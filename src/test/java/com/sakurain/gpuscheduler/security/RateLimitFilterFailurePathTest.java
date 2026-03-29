package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.config.JwtConfig;
import com.sakurain.gpuscheduler.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import io.lettuce.core.api.StatefulRedisConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimitFilter Redis异常降级路径测试。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterFailurePathTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(jwtUtil, jwtConfig, new ObjectMapper(), applicationContext);
        when(applicationContext.getBean("lettuceRedisConnection", StatefulRedisConnection.class))
                .thenThrow(new RedisConnectionFailureException("redis down"));
    }

    @Test
    void authPath_failClosed_returns503() throws Exception {
        ReflectionTestUtils.setField(rateLimitFilter, "failClosedAuth", true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        verify(filterChain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonAuthPath_failOpen_passesThrough() throws Exception {
        ReflectionTestUtils.setField(rateLimitFilter, "failClosedAuth", true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/monitor/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}

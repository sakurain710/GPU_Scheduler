package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IdempotencyFilter Redis异常降级路径测试。
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyFilterFailurePathTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FilterChain filterChain;

    private IdempotencyFilter idempotencyFilter;

    @BeforeEach
    void setUp() {
        idempotencyFilter = new IdempotencyFilter(redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));
    }

    @Test
    void redisUnavailable_failsOpen_andContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/task/submit");
        request.addHeader("X-Request-Id", "req-123");
        request.setContentType("application/json");
        request.setContent("{\"title\":\"t\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicatedRequestIdWithDifferentBody_returns409WithUnifiedErrorCode() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> localRedisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> localValueOps = Mockito.mock(ValueOperations.class);
        IdempotencyFilter filter = new IdempotencyFilter(localRedisTemplate, new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();

        String cached = mapper.writeValueAsString(new CachedResponseFixture(
                "old-fingerprint",
                200,
                "application/json;charset=UTF-8",
                "{\"code\":200}"
        ));
        when(localRedisTemplate.opsForValue()).thenReturn(localValueOps);
        when(localValueOps.get(anyString())).thenReturn(cached);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/task/submit");
        request.addHeader("X-Request-Id", "req-123");
        request.setContentType("application/json");
        request.setContent("{\"title\":\"new\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(409);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("errorCode").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    private record CachedResponseFixture(
            String fingerprint,
            int httpStatus,
            String contentType,
            String responseBody
    ) {
    }
}

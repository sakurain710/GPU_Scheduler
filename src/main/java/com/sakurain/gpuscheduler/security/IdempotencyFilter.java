package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性过滤器（防重复提交）
 * 对 POST / PUT / DELETE 请求，客户端可通过 X-Request-Id 头提供唯一键。
 * - 首次请求：正常处理，将响应体缓存到 Redis（TTL 可配置）
 * - 重复请求：直接返回缓存的响应体，HTTP 200
 * - 未携带 X-Request-Id：不做幂等处理，正常放行
 * Redis 不可用时 fail-open（记录警告，正常放行）
 */
@Slf4j
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "X-Request-Id";
    private static final String KEY_PREFIX = "gpu-scheduler:idempotency:";
    private static final Set<String> APPLICABLE_METHODS = Set.of("POST", "PUT", "DELETE");

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${idempotency.ttl:86400}")
    private long ttlSeconds;

    @Autowired
    public IdempotencyFilter(RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 只处理状态变更请求
        if (!APPLICABLE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = request.getHeader(IDEMPOTENCY_HEADER);
        if (!StringUtils.hasText(requestId)) {
            // 未携带幂等键，直接放行
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String redisKey = KEY_PREFIX + requestId;

            // 检查是否为重复请求
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.info("检测到重复请求，返回缓存响应: requestId={}", requestId);
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(cached);
                return;
            }

            // 首次请求：包装响应以捕获响应体
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(request, wrappedResponse);

            // 缓存响应体到 Redis
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            if (responseBody.length > 0) {
                String responseBodyStr = new String(responseBody, StandardCharsets.UTF_8);
                redisTemplate.opsForValue().set(redisKey, responseBodyStr, ttlSeconds, TimeUnit.SECONDS);
                log.debug("响应已缓存到幂等键: requestId={}, ttl={}s", requestId, ttlSeconds);
            }

            // 将响应体写回原始响应
            wrappedResponse.copyBodyToResponse();

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过幂等性检查（fail-open）: {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }
}

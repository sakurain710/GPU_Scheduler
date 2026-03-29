package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 幂等过滤器：支持请求指纹校验，防止同一个X-Request-Id被不同请求体复用
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
        if (!APPLICABLE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = request.getHeader(IDEMPOTENCY_HEADER);
        if (!StringUtils.hasText(requestId)) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] requestBody = request.getInputStream().readAllBytes();
        CachedBodyRequest wrappedRequest = new CachedBodyRequest(request, requestBody);
        String fingerprint = calculateFingerprint(wrappedRequest, requestBody);

        try {
            String redisKey = KEY_PREFIX + requestId;
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                IdempotentCachedResponse cachedResponse = objectMapper.readValue(cached, IdempotentCachedResponse.class);
                if (!fingerprint.equals(cachedResponse.fingerprint())) {
                    writeConflictResponse(response);
                    return;
                }

                response.setContentType(cachedResponse.contentType());
                response.setStatus(cachedResponse.httpStatus());
                response.getWriter().write(cachedResponse.responseBody());
                return;
            }

            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            String responseBodyStr = new String(responseBody, StandardCharsets.UTF_8);
            String contentType = wrappedResponse.getContentType() != null
                    ? wrappedResponse.getContentType()
                    : "application/json;charset=UTF-8";

            IdempotentCachedResponse toCache = new IdempotentCachedResponse(
                    fingerprint,
                    wrappedResponse.getStatus(),
                    contentType,
                    responseBodyStr
            );
            redisTemplate.opsForValue().set(
                    redisKey,
                    objectMapper.writeValueAsString(toCache),
                    ttlSeconds,
                    TimeUnit.SECONDS
            );

            wrappedResponse.copyBodyToResponse();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis不可用，幂等校验降级为fail-open: {}", e.getMessage());
            filterChain.doFilter(wrappedRequest, response);
        }
    }

    private String calculateFingerprint(HttpServletRequest request, byte[] requestBody) {
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        String raw = request.getMethod() + "\n" + request.getRequestURI() + "\n" + query + "\n"
                + Base64.getEncoder().encodeToString(requestBody);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256算法不可用", e);
        }
    }

    private void writeConflictResponse(HttpServletResponse response) throws IOException {
        response.setStatus(409);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.<Void>builder()
                .code(409)
                .message("X-Request-Id重复且请求内容不一致")
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private record IdempotentCachedResponse(
            String fingerprint,
            int httpStatus,
            String contentType,
            String responseBody
    ) {}

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}

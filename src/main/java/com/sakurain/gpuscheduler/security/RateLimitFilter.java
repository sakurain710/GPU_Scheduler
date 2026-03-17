package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.config.JwtConfig;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.util.JwtUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 限流过滤器
 * - 登录接口：每 IP 每分钟最多 N 次（防暴力破解）
 * - 其他接口：每用户/IP 每分钟最多 N 次
 * Redis 不可用时 fail-open（记录警告，放行请求）
 * ProxyManager 延迟初始化，避免测试环境（无 Redis）启动失败
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String LOGIN_KEY_PREFIX = "gpu-scheduler:ratelimit:login:";
    private static final String API_KEY_PREFIX = "gpu-scheduler:ratelimit:api:";

    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    /** 延迟初始化，避免 Redis 未就绪时启动失败 */
    private volatile LettuceBasedProxyManager<String> proxyManager;

    @Value("${rate-limit.login.capacity:5}")
    private long loginCapacity;

    @Value("${rate-limit.login.refill-duration:60}")
    private long loginRefillDuration;

    @Value("${rate-limit.api.capacity:100}")
    private long apiCapacity;

    @Value("${rate-limit.api.refill-duration:60}")
    private long apiRefillDuration;

    @Autowired
    public RateLimitFilter(JwtUtil jwtUtil,
                           JwtConfig jwtConfig,
                           ObjectMapper objectMapper,
                           ApplicationContext applicationContext) {
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            LettuceBasedProxyManager<String> pm = getProxyManager();

            String path = request.getRequestURI();
            boolean isLoginPath = LOGIN_PATH.equals(path);

            String key = isLoginPath
                    ? LOGIN_KEY_PREFIX + getClientIp(request)
                    : API_KEY_PREFIX + resolveApiKey(request);

            BucketConfiguration config = isLoginPath
                    ? buildConfig(loginCapacity, loginRefillDuration)
                    : buildConfig(apiCapacity, apiRefillDuration);

            Bucket bucket = pm.builder().build(key, () -> config);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                response.setHeader("X-RateLimit-Remaining",
                        String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
                log.warn("请求频率超限: path={}, key={}, retryAfter={}s", path, key, retryAfterSeconds);
                writeTooManyRequestsResponse(response, retryAfterSeconds);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过限流检查（fail-open）: {}", e.getMessage());
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // 捕获延迟初始化失败（Redis 不可用）
            if (isRedisUnavailable(e)) {
                log.warn("Redis 不可用，跳过限流检查（fail-open）: {}", e.getMessage());
                filterChain.doFilter(request, response);
            } else {
                throw e;
            }
        }
    }

    /**
     * 延迟获取 ProxyManager，首次调用时初始化（double-checked locking）
     */
    @SuppressWarnings("unchecked")
    private LettuceBasedProxyManager<String> getProxyManager() {
        if (proxyManager == null) {
            synchronized (this) {
                if (proxyManager == null) {
                    StatefulRedisConnection<String, byte[]> conn =
                            applicationContext.getBean("lettuceRedisConnection",
                                    StatefulRedisConnection.class);
                    proxyManager = LettuceBasedProxyManager.builderFor(conn).build();
                }
            }
        }
        return proxyManager;
    }

    private boolean isRedisUnavailable(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RedisConnectionFailureException
                    || cause.getClass().getName().contains("RedisConnection")
                    || (cause.getMessage() != null && cause.getMessage().contains("Unable to connect"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private BucketConfiguration buildConfig(long capacity, long refillDurationSeconds) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofSeconds(refillDurationSeconds))
                        .build())
                .build();
    }

    /**
     * 提取用于 API 限流的标识：优先使用 JWT 中的 userId，否则降级到客户端 IP
     */
    private String resolveApiKey(HttpServletRequest request) {
        try {
            String jwt = jwtUtil.extractTokenFromHeader(
                    request.getHeader(jwtConfig.getHeaderName()));
            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
                Long userId = jwtUtil.getUserIdFromToken(jwt);
                return "user:" + userId;
            }
        } catch (Exception ignored) {
            // 无法解析令牌时降级到 IP
        }
        return "ip:" + getClientIp(request);
    }

    /**
     * 获取客户端真实 IP（兼容反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequestsResponse(HttpServletResponse response,
                                               long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Remaining", "0");

        Result<Void> result = Result.<Void>builder()
                .code(429)
                .message("请求过于频繁，请稍后重试")
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}

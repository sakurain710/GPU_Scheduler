package com.sakurain.gpuscheduler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务
 * 使用 Redis 存储已吊销的令牌（通过 SHA-256 哈希标识，避免存储原始令牌）
 * Redis 不可用时采用 fail-open 策略（记录警告日志，放行请求）
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "gpu-scheduler:blacklist:";
    private static final String BLACKLISTED_VALUE = "1";

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public TokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将令牌加入黑名单，TTL 为令牌剩余有效时间
     *
     * @param token      JWT 令牌原文
     * @param expiration 令牌过期时间
     */
    public void blacklistToken(String token, Date expiration) {
        try {
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds <= 0) {
                // 令牌已过期，无需加入黑名单
                return;
            }
            String key = KEY_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, BLACKLISTED_VALUE, ttlSeconds, TimeUnit.SECONDS);
            log.debug("令牌已加入黑名单，TTL={}s", ttlSeconds);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，无法将令牌加入黑名单（fail-open）: {}", e.getMessage());
        }
    }

    /**
     * 检查令牌是否在黑名单中
     * Redis 不可用时返回 false（fail-open）
     *
     * @param token JWT 令牌原文
     * @return true 表示已被吊销
     */
    public boolean isBlacklisted(String token) {
        try {
            String key = KEY_PREFIX + hashToken(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过黑名单检查（fail-open）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 计算令牌的 SHA-256 哈希值（十六进制字符串）
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，不会发生此异常
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}

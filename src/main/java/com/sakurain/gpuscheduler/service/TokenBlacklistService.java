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
 * Token blacklist service backed by Redis.
 * Uses fail-open behavior when Redis is unavailable.
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "gpu-scheduler:blacklist:";
    private static final String ACCESS_REVOKE_CUTOFF_PREFIX = "gpu-scheduler:access-revoke-cutoff:user:";
    private static final String BLACKLISTED_VALUE = "1";

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public TokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Add a token to blacklist with TTL based on its remaining lifetime.
     */
    public void blacklistToken(String token, Date expiration) {
        try {
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds <= 0) {
                return;
            }
            String key = KEY_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, BLACKLISTED_VALUE, ttlSeconds, TimeUnit.SECONDS);
            log.debug("token blacklisted, ttl={}s", ttlSeconds);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, cannot blacklist token (fail-open): {}", e.getMessage());
        }
    }

    /**
     * Check whether token is blacklisted.
     */
    public boolean isBlacklisted(String token) {
        try {
            String key = KEY_PREFIX + hashToken(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skip blacklist check (fail-open): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Revoke all access tokens issued before cutoff time for one user.
     */
    public void revokeAccessTokensIssuedBefore(Long userId, Date cutoff, Date expiration) {
        try {
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds <= 0) {
                return;
            }
            String key = ACCESS_REVOKE_CUTOFF_PREFIX + userId;
            redisTemplate.opsForValue().set(key, String.valueOf(cutoff.getTime()), ttlSeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, cannot write access revoke cutoff (fail-open): {}", e.getMessage());
        }
    }

    /**
     * Check whether an access token was issued before user's revoke cutoff.
     */
    public boolean isAccessTokenRevokedByRefresh(Long userId, Date issuedAt) {
        try {
            String key = ACCESS_REVOKE_CUTOFF_PREFIX + userId;
            String cutoffRaw = redisTemplate.opsForValue().get(key);
            if (cutoffRaw == null) {
                return false;
            }
            long cutoffMs = Long.parseLong(cutoffRaw);
            return issuedAt.getTime() < cutoffMs;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skip access revoke cutoff check (fail-open): {}", e.getMessage());
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * SHA-256 hash for token key derivation.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

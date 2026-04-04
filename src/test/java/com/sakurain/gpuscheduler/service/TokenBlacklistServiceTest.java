package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenBlacklistService = new TokenBlacklistService(redisTemplate, true);
    }

    @Test
    void revokeAccessTokensIssuedBefore_shouldStoreCutoffInEpochSeconds() {
        Date cutoff = new Date(1_700_000_000_987L);
        Date expiration = new Date(System.currentTimeMillis() + 60_000);

        tokenBlacklistService.revokeAccessTokensIssuedBefore(9L, cutoff, expiration);

        verify(valueOperations).set(
                eq("gpu-scheduler:access-revoke-cutoff:user:9"),
                eq(String.valueOf(cutoff.getTime() / 1000)),
                anyLong(),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void isAccessTokenRevokedByRefresh_shouldSupportLegacyMillisecondCutoff() {
        when(valueOperations.get("gpu-scheduler:access-revoke-cutoff:user:5"))
                .thenReturn("1700000001500");

        boolean revoked = tokenBlacklistService.isAccessTokenRevokedByRefresh(5L, new Date(1_700_000_000_000L));

        assertTrue(revoked);
    }

    @Test
    void isAccessTokenRevokedByRefresh_shouldUseEpochSecondComparison() {
        when(valueOperations.get("gpu-scheduler:access-revoke-cutoff:user:6"))
                .thenReturn("1700000001");

        boolean revoked = tokenBlacklistService.isAccessTokenRevokedByRefresh(6L, new Date(1_700_000_001_900L));

        assertFalse(revoked);
    }

    @Test
    void isBlacklisted_whenRedisUnavailableAndFailClosed_shouldThrowBusinessException() {
        TokenBlacklistService failClosedService = new TokenBlacklistService(redisTemplate, false);
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis down"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> failClosedService.isBlacklisted("token"));
        assertTrue("AUTH_BLACKLIST_BACKEND_UNAVAILABLE".equals(ex.getCode()));
    }
}

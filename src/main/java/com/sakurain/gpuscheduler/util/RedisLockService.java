package com.sakurain.gpuscheduler.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁服务
 */
@Slf4j
@Service
public class RedisLockService {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> unlockRedisScript;

    public RedisLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockRedisScript = new DefaultRedisScript<>();
        this.unlockRedisScript.setScriptText(UNLOCK_SCRIPT);
        this.unlockRedisScript.setResultType(Long.class);
    }

    /**
     * 尝试获取锁
     *
     * @param key     锁的key
     * @param value   锁的value（通常是唯一标识）
     * @param timeout 锁的超时时间
     * @param unit    时间单位
     * @return true如果获取成功，false否则
     */
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放锁
     *
     * @param key   锁的key
     * @param value 锁的value（用于验证是否是自己持有的锁）
     * Atomic unlock: delete only when current lock value equals owner value.
     */
    public void unlock(String key, String value) {
        Long released = redisTemplate.execute(unlockRedisScript, Collections.singletonList(key), value);
        if (released == 0) {
            log.debug("跳过释放锁，锁未拥有或已过期: key={}", key);
        }
    }
}

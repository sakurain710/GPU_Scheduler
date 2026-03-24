package com.sakurain.gpuscheduler.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁服务
 */
@Slf4j
@Service
public class RedisLockService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
     */
    public void unlock(String key, String value) {
        String currentValue = redisTemplate.opsForValue().get(key);
        if (value.equals(currentValue)) {
            redisTemplate.delete(key);
        }
    }
}

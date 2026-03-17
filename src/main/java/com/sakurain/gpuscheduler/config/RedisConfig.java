package com.sakurain.gpuscheduler.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 配置 RedisTemplate 和 Bucket4j 所需的 Lettuce 原生连接
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate，使用 String 序列化器
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 暴露 Lettuce 原生连接，供 Bucket4j LettuceBasedProxyManager 使用
     * 键使用 String 编码，值使用 byte[] 编码（Bucket4j 要求）
     * 标记为 @Lazy，避免在测试环境中（无 Redis 时）启动失败
     */
    @Lazy
    @Bean
    public StatefulRedisConnection<String, byte[]> lettuceRedisConnection(
            LettuceConnectionFactory lettuceConnectionFactory) {
        RedisClient redisClient = (RedisClient) lettuceConnectionFactory.getNativeClient();
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }
}

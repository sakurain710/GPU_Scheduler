package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置类
 * 定义 token 过期时间、签名密钥等常量
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * JWT 签名密钥（建议从环境变量或配置文件注入，生产环境使用足够长的密钥）
     */
    private String secretKey = "your-256-bit-secret-key-for-jwt-signing-must-be-at-least-32-characters-long";

    /**
     * Access Token 过期时间（毫秒），默认 24 小时
     */
    private Long accessTokenExpiration = 86400000L;

    /**
     * Refresh Token 过期时间（毫秒），默认 7 天
     */
    private Long refreshTokenExpiration = 604800000L;

    /**
     * Token 发行者
     */
    private String issuer = "gpu-scheduler";

    /**
     * Token 请求头名称
     */
    private String headerName = "Authorization";

    /**
     * Token 前缀
     */
    private String tokenPrefix = "Bearer ";
}

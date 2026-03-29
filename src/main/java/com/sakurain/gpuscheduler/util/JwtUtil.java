package com.sakurain.gpuscheduler.util;

import com.sakurain.gpuscheduler.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 工具类
 * 提供 token 生成、解析、验证等功能
 */
@Slf4j
@Component
public class JwtUtil {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Autowired
    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        // 使用配置的密钥生成 HMAC-SHA 密钥
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtConfig.getSecretKey().getBytes())
        );
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param roles    角色列表
     * @return JWT token 字符串
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return generateToken(userId, username, roles, jwtConfig.getAccessTokenExpiration(), TOKEN_TYPE_ACCESS);
    }

    /**
     * 生成 Refresh Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT refresh token 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, null, jwtConfig.getRefreshTokenExpiration(), TOKEN_TYPE_REFRESH);
    }

    /**
     * 生成 JWT Token
     *
     * @param userId     用户ID
     * @param username   用户名
     * @param roles      角色列表（可选）
     * @param expiration 过期时间（毫秒）
     * @return JWT token 字符串
     */
    private String generateToken(Long userId,
                                 String username,
                                 List<String> roles,
                                 Long expiration,
                                 String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_TOKEN_TYPE, tokenType);
        if (roles != null && !roles.isEmpty()) {
            claims.put(CLAIM_ROLES, roles);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer(jwtConfig.getIssuer())
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 从 token 中解析 Claims
     *
     * @param token JWT token
     * @return Claims 对象
     * @throws JwtException 解析失败时抛出异常
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 token 是否有效
     *
     * @param token JWT token
     * @return true 如果 token 有效，false 否则
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token 已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("格式错误的 JWT token: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT 签名验证失败: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token 为空或非法: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 验证 token 是否有效（不抛出异常，返回详细结果）
     *
     * @param token JWT token
     * @return TokenValidationResult 验证结果
     */
    public TokenValidationResult validateTokenWithReason(String token) {
        try {
            parseToken(token);
            return TokenValidationResult.success();
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.failure("Token 已过期");
        } catch (UnsupportedJwtException e) {
            return TokenValidationResult.failure("不支持的 Token 格式");
        } catch (MalformedJwtException e) {
            return TokenValidationResult.failure("Token 格式错误");
        } catch (SignatureException e) {
            return TokenValidationResult.failure("Token 签名验证失败");
        } catch (IllegalArgumentException e) {
            return TokenValidationResult.failure("Token 为空或非法");
        }
    }

    /**
     * 从 token 中获取用户ID
     *
     * @param token JWT token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get(CLAIM_USER_ID);
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从 token 中获取用户名
     *
     * @param token JWT token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USERNAME, String.class);
    }

    /**
     * 从 token 中获取角色列表
     *
     * @param token JWT token
     * @return 角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        Object roles = claims.get(CLAIM_ROLES);
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return List.of();
    }

    /**
     * 从 token 中获取过期时间
     *
     * @param token JWT token
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 从 token 中获取令牌类型：access 或 refresh
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_TOKEN_TYPE, String.class);
    }

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }

    /**
     * 检查 token 是否即将过期（默认 5 分钟内）
     *
     * @param token JWT token
     * @return true 如果 token 即将过期
     */
    public boolean isTokenExpiringSoon(String token) {
        return isTokenExpiringSoon(token, 5 * 60 * 1000L);
    }

    /**
     * 检查 token 是否即将过期
     *
     * @param token     JWT token
     * @param threshold 时间阈值（毫秒）
     * @return true 如果 token 在阈值时间内过期
     */
    public boolean isTokenExpiringSoon(String token, Long threshold) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration.getTime() - System.currentTimeMillis() < threshold;
    }

    /**
     * 从 Authorization Header 中提取 token
     *
     * @param authorizationHeader Authorization header 值
     * @return token 字符串，如果不符合格式则返回 null
     */
    public String extractTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(jwtConfig.getTokenPrefix())) {
            return authorizationHeader.substring(jwtConfig.getTokenPrefix().length());
        }
        return null;
    }

    /**
     * Token 验证结果记录类
     */
    public record TokenValidationResult(boolean valid, String message) {
        public static TokenValidationResult success() {
            return new TokenValidationResult(true, null);
        }

        public static TokenValidationResult failure(String message) {
            return new TokenValidationResult(false, message);
        }
    }
}

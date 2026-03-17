package com.sakurain.gpuscheduler.util;

import com.sakurain.gpuscheduler.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类测试
 */
@SpringBootTest
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtConfig jwtConfig;

    private Long testUserId;
    private String testUsername;
    private List<String> testRoles;

    @BeforeEach
    void setUp() {
        testUserId = 1001L;
        testUsername = "testuser";
        testRoles = List.of("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void testGenerateAccessToken() {
        // 生成 access token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 验证 token 不为空
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // 验证 token 格式（JWT 由三部分组成，用 . 分隔）
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT token 应该包含三部分");
    }

    @Test
    void testGenerateRefreshToken() {
        // 生成 refresh token
        String token = jwtUtil.generateRefreshToken(testUserId, testUsername);

        // 验证 token 不为空
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // 验证 refresh token 不包含角色信息
        Claims claims = jwtUtil.parseToken(token);
        assertNull(claims.get("roles"));
    }

    @Test
    void testParseToken() {
        // 生成 token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 解析 token
        Claims claims = jwtUtil.parseToken(token);

        // 验证 claims 内容
        assertNotNull(claims);
        assertEquals(testUserId, ((Number) claims.get("userId")).longValue());
        assertEquals(testUsername, claims.get("username"));
        assertEquals(testRoles, claims.get("roles"));
        assertEquals(jwtConfig.getIssuer(), claims.getIssuer());
    }

    @Test
    void testValidateToken() {
        // 生成有效 token
        String validToken = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 验证有效 token
        assertTrue(jwtUtil.validateToken(validToken));

        // 验证无效 token
        String invalidToken = "invalid.token.string";
        assertFalse(jwtUtil.validateToken(invalidToken));
    }

    @Test
    void testValidateTokenWithReason() {
        // 生成有效 token
        String validToken = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 验证有效 token
        JwtUtil.TokenValidationResult result = jwtUtil.validateTokenWithReason(validToken);
        assertTrue(result.valid());
        assertNull(result.message());

        // 验证无效 token
        String invalidToken = "invalid.token.string";
        result = jwtUtil.validateTokenWithReason(invalidToken);
        assertFalse(result.valid());
        assertNotNull(result.message());
    }

    @Test
    void testGetUserIdFromToken() {
        // 生成 token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 从 token 中获取用户ID
        Long userId = jwtUtil.getUserIdFromToken(token);

        // 验证用户ID
        assertEquals(testUserId, userId);
    }

    @Test
    void testGetUsernameFromToken() {
        // 生成 token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 从 token 中获取用户名
        String username = jwtUtil.getUsernameFromToken(token);

        // 验证用户名
        assertEquals(testUsername, username);
    }

    @Test
    void testGetRolesFromToken() {
        // 生成 token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 从 token 中获取角色列表
        List<String> roles = jwtUtil.getRolesFromToken(token);

        // 验证角色列表
        assertEquals(testRoles.size(), roles.size());
        assertTrue(roles.containsAll(testRoles));
    }

    @Test
    void testGetExpirationDateFromToken() {
        // 生成 token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 从 token 中获取过期时间
        Date expirationDate = jwtUtil.getExpirationDateFromToken(token);

        // 验证过期时间在未来
        assertTrue(expirationDate.after(new Date()));

        // 验证过期时间大约在 24 小时后（允许 1 分钟误差）
        long expectedExpiration = System.currentTimeMillis() + jwtConfig.getAccessTokenExpiration();
        long actualExpiration = expirationDate.getTime();
        assertTrue(Math.abs(expectedExpiration - actualExpiration) < 60000);
    }

    @Test
    void testIsTokenExpiringSoon() {
        // 生成 token
        String token = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);

        // 新生成的 token 不应该即将过期
        assertFalse(jwtUtil.isTokenExpiringSoon(token));

        // 测试自定义阈值（24 小时）
        assertTrue(jwtUtil.isTokenExpiringSoon(token, jwtConfig.getAccessTokenExpiration() + 1000));
    }

    @Test
    void testExtractTokenFromHeader() {
        // 测试有效的 Authorization header
        String token = "test.jwt.token";
        String header = jwtConfig.getTokenPrefix() + token;
        String extractedToken = jwtUtil.extractTokenFromHeader(header);
        assertEquals(token, extractedToken);

        // 测试无效的 header（没有前缀）
        String invalidHeader = "InvalidPrefix test.jwt.token";
        assertNull(jwtUtil.extractTokenFromHeader(invalidHeader));

        // 测试 null header
        assertNull(jwtUtil.extractTokenFromHeader(null));
    }

    @Test
    void testExpiredToken() {
        // 手动创建一个已过期的 token
        Date now = new Date();
        Date expiredDate = new Date(now.getTime() - 1000); // 1 秒前过期

        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtConfig.getSecretKey().getBytes())
        );
        SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);

        String expiredToken = Jwts.builder()
                .subject(String.valueOf(testUserId))
                .claim("userId", testUserId)
                .claim("username", testUsername)
                .issuedAt(new Date(now.getTime() - 2000))
                .expiration(expiredDate)
                .issuer(jwtConfig.getIssuer())
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        // 验证过期 token
        assertFalse(jwtUtil.validateToken(expiredToken));

        // 验证过期 token 的详细原因
        JwtUtil.TokenValidationResult result = jwtUtil.validateTokenWithReason(expiredToken);
        assertFalse(result.valid());
        assertTrue(result.message().contains("过期"));

        // 尝试解析过期 token 应该抛出异常
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parseToken(expiredToken));
    }

    @Test
    void testTokenWithoutRoles() {
        // 生成不包含角色的 token（使用 refresh token）
        String token = jwtUtil.generateRefreshToken(testUserId, testUsername);

        // 验证可以正常解析
        assertTrue(jwtUtil.validateToken(token));

        // 验证角色列表为空
        List<String> roles = jwtUtil.getRolesFromToken(token);
        assertTrue(roles.isEmpty());
    }

    @Test
    void testAccessTokenExpiration() {
        // 生成 access token
        String accessToken = jwtUtil.generateAccessToken(testUserId, testUsername, testRoles);
        Date accessExpiration = jwtUtil.getExpirationDateFromToken(accessToken);

        // 生成 refresh token
        String refreshToken = jwtUtil.generateRefreshToken(testUserId, testUsername);
        Date refreshExpiration = jwtUtil.getExpirationDateFromToken(refreshToken);

        // 验证 refresh token 的过期时间晚于 access token
        assertTrue(refreshExpiration.after(accessExpiration));
    }
}

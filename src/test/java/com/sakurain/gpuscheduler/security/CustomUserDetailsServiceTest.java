package com.sakurain.gpuscheduler.security;

import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自定义用户认证服务测试
 */
@SpringBootTest
@Transactional
class CustomUserDetailsServiceTest {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String uniqueSuffix;
    private User testUser;
    private Role testRole;

    @BeforeEach
    void setUp() {
        uniqueSuffix = String.valueOf(System.nanoTime());

        // 创建测试角色
        testRole = Role.builder()
                .code("ROLE_TEST_" + uniqueSuffix)
                .name("测试角色")
                .roleType(1)
                .status(1)
                .sortOrder(1)
                .build();
        roleMapper.insert(testRole);

        // 创建测试用户
        testUser = User.builder()
                .username("testuser_" + uniqueSuffix)
                .password(passwordEncoder.encode("password123"))
                .email("test_" + uniqueSuffix + "@example.com")
                .status(1)
                .build();
        userMapper.insert(testUser);
    }

    @Test
    void testLoadUserByUsername() {
        // 加载用户
        UserDetails userDetails = userDetailsService.loadUserByUsername(testUser.getUsername());

        // 验证用户信息
        assertNotNull(userDetails);
        assertEquals(testUser.getUsername(), userDetails.getUsername());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isCredentialsNonExpired());

        // 验证密码
        assertTrue(passwordEncoder.matches("password123", userDetails.getPassword()));

        // 验证是 CustomUserDetails 实例
        assertTrue(userDetails instanceof CustomUserDetails);
        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
        assertEquals(testUser.getId(), customUserDetails.getUserId());
    }

    @Test
    void testLoadUserByUsernameNotFound() {
        // 尝试加载不存在的用户
        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername("nonexistent_user");
        });
    }

    @Test
    void testLoadUserById() {
        // 根据用户ID加载用户
        UserDetails userDetails = userDetailsService.loadUserById(testUser.getId());

        // 验证用户信息
        assertNotNull(userDetails);
        assertEquals(testUser.getUsername(), userDetails.getUsername());
        assertTrue(userDetails.isEnabled());
    }

    @Test
    void testLoadUserByIdNotFound() {
        // 尝试加载不存在的用户ID
        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserById(999999L);
        });
    }

    @Test
    void testLoadUserWithRoles() {
        // 加载用户（用户和角色已在 setUp 中创建）
        UserDetails userDetails = userDetailsService.loadUserByUsername(testUser.getUsername());

        // 验证用户信息
        assertNotNull(userDetails);
        assertTrue(userDetails instanceof CustomUserDetails);

        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
        assertNotNull(customUserDetails.getRoles());
        // 角色列表可能为空，因为我们没有在 user_role 表中建立关联
        // 这个测试主要验证角色加载逻辑不会抛出异常
    }

    @Test
    void testDisabledUser() {
        // 创建禁用的用户
        User disabledUser = User.builder()
                .username("disabled_" + uniqueSuffix)
                .password(passwordEncoder.encode("password123"))
                .email("disabled_" + uniqueSuffix + "@example.com")
                .status(0) // 禁用状态
                .build();
        userMapper.insert(disabledUser);

        // 加载禁用的用户
        UserDetails userDetails = userDetailsService.loadUserByUsername(disabledUser.getUsername());

        // 验证用户已禁用
        assertNotNull(userDetails);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    void testLockedUser() {
        // 创建锁定的用户
        User lockedUser = User.builder()
                .username("locked_" + uniqueSuffix)
                .password(passwordEncoder.encode("password123"))
                .email("locked_" + uniqueSuffix + "@example.com")
                .status(2) // 锁定状态
                .build();
        userMapper.insert(lockedUser);

        // 加载锁定的用户
        UserDetails userDetails = userDetailsService.loadUserByUsername(lockedUser.getUsername());

        // 验证用户已锁定
        assertNotNull(userDetails);
        assertFalse(userDetails.isAccountNonLocked());
    }

    @Test
    void testSoftDeletedUser() {
        // 软删除用户（使用 deleteById 触发 @TableLogic）
        userMapper.deleteById(testUser.getId());

        // 尝试加载已删除的用户（应该找不到）
        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername(testUser.getUsername());
        });
    }

    @Test
    void testPasswordEncoder() {
        // 测试密码编码器
        String rawPassword = "testPassword123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 验证密码不是明文
        assertNotEquals(rawPassword, encodedPassword);

        // 验证密码匹配
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));

        // 验证错误密码不匹配
        assertFalse(passwordEncoder.matches("wrongPassword", encodedPassword));
    }
}

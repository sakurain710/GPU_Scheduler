package com.sakurain.gpuscheduler.mapper;

import com.sakurain.gpuscheduler.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserMapper 测试类
 */
@SpringBootTest
@Transactional
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testInsertAndSelectById() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser")
                .password("$2a$10$encrypted_password")
                .nickname("测试用户")
                .email("test@example.com")
                .mobile("13800138000")
                .gender(1)
                .userType(1)
                .status(1)
                .build();

        // 插入
        int result = userMapper.insert(user);
        assertEquals(1, result);
        assertNotNull(user.getId());

        // 查询
        User found = userMapper.selectById(user.getId());
        assertNotNull(found);
        assertEquals("testuser", found.getUsername());
        assertEquals("test@example.com", found.getEmail());
    }

    @Test
    void testSelectByUsername() {
        // 创建测试用户
        User user = User.builder()
                .username("uniqueuser")
                .password("$2a$10$encrypted_password")
                .nickname("唯一用户")
                .email("unique@example.com")
                .gender(0)
                .userType(1)
                .status(1)
                .build();
        userMapper.insert(user);

        // 按用户名查询
        User found = userMapper.selectByUsername("uniqueuser");
        assertNotNull(found);
        assertEquals("uniqueuser", found.getUsername());

        // 查询不存在的用户名
        User notFound = userMapper.selectByUsername("nonexistent");
        assertNull(notFound);
    }

    @Test
    void testSoftDelete() {
        User user = User.builder()
                .username("softdeleteuser")
                .password("$2a$10$encrypted_password")
                .gender(0)
                .userType(1)
                .status(1)
                .build();
        userMapper.insert(user);

        // 使用 deleteById 触发 @TableLogic 软删除（自动执行 SET deleted_at = now()）
        userMapper.deleteById(user.getId());

        // 软删除后按用户名查询应返回 null
        User found = userMapper.selectByUsername("softdeleteuser");
        assertNull(found);
    }

    @Test
    void testUpdateUser() {
        User user = User.builder()
                .username("updateuser")
                .password("$2a$10$encrypted_password")
                .nickname("原始昵称")
                .gender(0)
                .userType(1)
                .status(1)
                .build();
        userMapper.insert(user);

        // 更新昵称
        user.setNickname("新昵称");
        userMapper.updateById(user);

        User updated = userMapper.selectById(user.getId());
        assertEquals("新昵称", updated.getNickname());
    }
}

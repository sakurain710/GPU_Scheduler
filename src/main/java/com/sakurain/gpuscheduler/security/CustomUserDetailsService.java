package com.sakurain.gpuscheduler.security;

import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自定义用户认证服务
 * 从数据库加载用户信息和角色信息
 */
@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Autowired
    public CustomUserDetailsService(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载用户信息: {}", username);

        // 从数据库查询用户
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        // 查询用户的角色列表
        List<Role> roles = roleMapper.selectByUserId(user.getId());
        log.debug("用户 {} 拥有 {} 个角色", username, roles.size());

        // 构建 CustomUserDetails
        return new CustomUserDetails(user, roles);
    }

    /**
     * 根据用户ID加载用户信息
     * 用于 JWT token 验证后重新加载用户信息
     */
    public UserDetails loadUserById(Long userId) {
        log.debug("根据用户ID加载用户信息: {}", userId);

        // 从数据库查询用户
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            log.warn("用户不存在或已删除: {}", userId);
            throw new UsernameNotFoundException("用户不存在");
        }

        // 查询用户的角色列表
        List<Role> roles = roleMapper.selectByUserId(user.getId());

        // 构建 CustomUserDetails
        return new CustomUserDetails(user, roles);
    }
}

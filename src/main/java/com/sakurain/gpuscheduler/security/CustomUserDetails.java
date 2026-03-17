package com.sakurain.gpuscheduler.security;

import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义 UserDetails 实现
 * 封装用户认证信息，包括用户ID、用户名、密码、角色等
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Integer status;
    private final List<Role> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user, List<Role> roles) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.status = user.getStatus();
        this.roles = roles;
        // 将角色转换为 Spring Security 的 GrantedAuthority
        this.authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getCode()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return status != 2; // status=2 表示锁定
    }

    /**
     * 凭证（密码）是否未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否启用
     */
    @Override
    public boolean isEnabled() {
        return status == 1; // status=1 表示启用
    }

    /**
     * 获取角色编码列表
     */
    public List<String> getRoleCodes() {
        return roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
    }
}

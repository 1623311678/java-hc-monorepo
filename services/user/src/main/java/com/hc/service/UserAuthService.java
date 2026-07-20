package com.hc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hc.dto.LoginRequest;
import com.hc.dto.LoginResponse;
import com.hc.dto.RegisterRequest;
import com.hc.dto.UserProfile;
import com.hc.model.User;
import com.hc.repository.UserMapper;
import com.hc.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户认证服务
 */
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserProfile register(RegisterRequest request) {
        User existing = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (StringUtils.hasText(request.getPhone())) {
            User phoneUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone())
            );
            if (phoneUser != null) {
                throw new IllegalArgumentException("手机号已存在");
            }
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setStatus(1);

        userMapper.insert(user);
        return toProfile(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalArgumentException("账号已禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
            .token(token)
            .user(toProfile(user))
            .build();
    }

    public UserProfile me(String token) {
        Long userId = jwtUtil.extractUserId(token);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return toProfile(user);
    }

    private UserProfile toProfile(User user) {
        return UserProfile.builder()
            .id(user.getId())
            .username(user.getUsername())
            .phone(user.getPhone())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .avatar(user.getAvatar())
            .status(user.getStatus())
            .build();
    }
}

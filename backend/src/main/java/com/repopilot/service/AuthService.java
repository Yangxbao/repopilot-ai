package com.repopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.dto.auth.AuthResponse;
import com.repopilot.dto.auth.LoginRequest;
import com.repopilot.dto.auth.RegisterRequest;
import com.repopilot.dto.user.UserVO;
import com.repopilot.entity.User;
import com.repopilot.mapper.UserMapper;
import com.repopilot.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 注册 / 登录 / 用户信息。
 *
 * <p>约定：系统中的第一个注册用户自动成为 ADMIN（便于个人部署后自我管理），
 * 之后的用户都是普通 USER。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        // 第一个用户是管理员
        user.setRole(userMapper.selectCount(null) == 0 ? "ADMIN" : "USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username())
                .last("LIMIT 1"));
        // 用户不存在与密码错误返回同一条消息，避免泄露"用户名是否存在"
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        return buildResponse(user);
    }

    public UserVO me(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getStatus(),
                user.getCreatedAt());
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, 72, new AuthResponse.UserVO(
                user.getId(), user.getUsername(), user.getNickname(), user.getRole()));
    }
}

package com.taskflow.service;

import com.taskflow.entity.User;
import com.taskflow.exception.BizException;
import com.taskflow.mapper.UserMapper;
import java.util.regex.Pattern;

/**
 * 用户注册/登录的业务逻辑（演示数据）。
 *
 * 注册流程（这是体验问答的典型问题："用户注册流程在哪里实现？"）：
 * 1. UserController.register() 接收参数；
 * 2. 调用 UserService.register()；
 * 3. 这里先校验用户名格式与重复性，再对密码做 BCrypt 哈希；
 * 4. 最后通过 UserMapper.insert() 落库。
 */
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String rawPassword, String nickname) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BizException(400, "用户名只能包含字母、数字和下划线，长度 3-32");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BizException(400, "密码长度至少 6 位");
        }
        if (userMapper.findByUsername(username) != null) {
            throw new BizException(409, "用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname == null ? username : nickname);
        userMapper.insert(user);
        return user;
    }

    public User login(String username, String rawPassword) {
        User user = userMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        return user;
    }
}

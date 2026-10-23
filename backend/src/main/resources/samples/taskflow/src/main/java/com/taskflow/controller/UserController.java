package com.taskflow.controller;

import com.taskflow.common.ApiResponse;
import com.taskflow.entity.User;
import com.taskflow.service.UserService;
import java.util.Map;

/**
 * 用户接口（演示数据）。
 * 注册/登录的 HTTP 入口，实际业务在 UserService。
 */
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public ApiResponse<User> register(Map<String, String> body) {
        User user = userService.register(body.get("username"), body.get("password"), body.get("nickname"));
        return ApiResponse.ok(user);
    }

    public ApiResponse<User> login(Map<String, String> body) {
        User user = userService.login(body.get("username"), body.get("password"));
        return ApiResponse.ok(user);
    }
}

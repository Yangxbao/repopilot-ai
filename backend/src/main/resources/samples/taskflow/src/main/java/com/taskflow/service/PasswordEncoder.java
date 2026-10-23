package com.taskflow.service;

/**
 * 密码编码器接口（演示数据）。
 */
public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}

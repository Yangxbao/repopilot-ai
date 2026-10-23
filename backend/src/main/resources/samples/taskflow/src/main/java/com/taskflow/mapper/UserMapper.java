package com.taskflow.mapper;

import com.taskflow.entity.User;

/**
 * 用户数据访问接口（演示数据）。
 */
public interface UserMapper {

    User findById(Long id);

    User findByUsername(String username);

    void insert(User user);
}

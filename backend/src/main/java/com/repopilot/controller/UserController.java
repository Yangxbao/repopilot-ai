package com.repopilot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repopilot.common.ApiResponse;
import com.repopilot.common.PageResult;
import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.dto.user.UserVO;
import com.repopilot.entity.User;
import com.repopilot.mapper.UserMapper;
import com.repopilot.security.LoginUser;
import com.repopilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理（仅 ADMIN）。
 * 权限在方法上用 @PreAuthorize 声明 —— 后端真实校验，而不是只靠前端隐藏入口。
 */
@Tag(name = "用户管理（管理员）")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Operation(summary = "用户列表（分页）")
    @GetMapping
    public ApiResponse<PageResult<UserVO>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        size = Math.min(size, 50);
        Page<User> result = userMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
                new LambdaQueryWrapper<User>().orderByAsc(User::getId));
        List<UserVO> vos = result.getRecords().stream()
                .map(u -> new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getRole(),
                        u.getStatus(), u.getCreatedAt()))
                .toList();
        return ApiResponse.ok(new PageResult<>(vos, result.getTotal(), page, size));
    }

    @Operation(summary = "启用 / 禁用用户（不能禁用自己）")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable long id, @RequestBody StatusRequest request) {
        LoginUser current = SecurityUtils.currentUser();
        if (current.userId() == id) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能禁用自己的账号");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!"ACTIVE".equals(request.status()) && !"DISABLED".equals(request.status())) {
            throw new BizException(ErrorCode.VALIDATION, "状态只允许 ACTIVE / DISABLED");
        }
        user.setStatus(request.status());
        userMapper.updateById(user);
        return ApiResponse.ok();
    }

    public record StatusRequest(String status) {
    }
}

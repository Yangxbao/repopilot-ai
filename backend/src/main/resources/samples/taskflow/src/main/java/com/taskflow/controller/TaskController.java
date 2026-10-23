package com.taskflow.controller;

import com.taskflow.common.ApiResponse;
import com.taskflow.entity.Task;
import com.taskflow.service.TaskService;
import java.util.List;

/**
 * 任务接口（演示数据）。
 * Controller 只做参数接收与响应包装，业务在 TaskService 中 ——
 * 体验问答："TaskController 调用了哪些 Service？"
 */
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    public ApiResponse<Task> create(Long userId, String title, String description) {
        Task task = taskService.createTask(userId, title, description);
        return ApiResponse.ok(task);
    }

    public ApiResponse<Task> getTask(Long taskId) {
        // 演示堆栈中的 TaskController.java:38 对应这里
        Task task = taskService.getTaskDetail(taskId);
        return ApiResponse.ok(task);
    }

    public ApiResponse<Task> transition(Long taskId, String targetStatus) {
        Task task = taskService.transition(taskId, targetStatus);
        return ApiResponse.ok(task);
    }

    public ApiResponse<List<Task>> list(Long userId) {
        return ApiResponse.ok(taskService.listByUser(userId));
    }
}

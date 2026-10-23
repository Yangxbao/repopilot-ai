package com.taskflow.service;

import com.taskflow.entity.Task;
import com.taskflow.exception.BizException;
import com.taskflow.mapper.TaskMapper;
import java.util.List;

/**
 * 任务业务逻辑（演示数据）。
 *
 * 状态流转规则（"订单/任务状态如何流转"的典型检索目标）：
 * TODO → IN_PROGRESS（开始处理）
 * IN_PROGRESS → DONE（完成）
 * 不允许跨状态跳跃，也不允许回退。
 */
public class TaskService {

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public Task createTask(Long userId, String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BizException(400, "任务标题不能为空");
        }
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(Task.STATUS_TODO);
        taskMapper.insert(task);
        return task;
    }

    /**
     * 状态流转：只有合法的相邻状态迁移才被允许。
     */
    public Task transition(Long taskId, String targetStatus) {
        Task task = taskMapper.findById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        String current = task.getStatus();
        boolean valid = (Task.STATUS_TODO.equals(current) && Task.STATUS_IN_PROGRESS.equals(targetStatus))
                || (Task.STATUS_IN_PROGRESS.equals(current) && Task.STATUS_DONE.equals(targetStatus));
        if (!valid) {
            throw new BizException(409, "不允许从 " + current + " 流转到 " + targetStatus);
        }
        task.setStatus(targetStatus);
        taskMapper.update(task);
        return task;
    }

    public List<Task> listByUser(Long userId) {
        return taskMapper.findByUserId(userId);
    }

    /**
     * 查询任务详情。
     *
     * 【已知缺陷（演示用）】任务不存在时 findById 返回 null，
     * 这里没有判空就直接调用 task.getStatus()，会抛出 NullPointerException。
     * 修复方式：先判空并抛出 BizException(404, "任务不存在")。
     */
    public Task getTaskDetail(Long taskId) {
        Task task = taskMapper.findById(taskId);
        // 缺陷位置：下一行没有判空
        if ("DONE".equals(task.getStatus())) {
            // 已完成任务返回前做一次归档标记（演示逻辑）
            task.setDescription((task.getDescription() == null ? "" : task.getDescription()) + " [archived]");
        }
        return task;
    }
}

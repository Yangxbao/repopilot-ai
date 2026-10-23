package com.taskflow.mapper;

import com.taskflow.entity.Task;
import java.util.List;

/**
 * 任务数据访问接口（演示数据）。
 *findById 返回 null 表示任务不存在 —— 这正是演示 Bug 的来源：
 * 调用方如果不判空就会 NPE。
 */
public interface TaskMapper {

    Task findById(Long id);

    List<Task> findByUserId(Long userId);

    List<Task> findByStatus(String status);

    void insert(Task task);

    void update(Task task);
}

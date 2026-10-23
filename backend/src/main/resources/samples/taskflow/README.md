# TaskFlow —— RepoPilot 内置演示项目

> ⚠️ **这是随 RepoPilot AI 一起发布的演示数据，不是真实项目。**
> 用途：让没有现成代码包的用户体验「导入 → 索引 → 问答 → Bug 分析」的完整流程。

TaskFlow 是一个（虚构的）任务管理系统，包含用户注册/登录与任务管理两大模块：

- `UserController` / `UserService`：注册、登录流程
- `TaskController` / `TaskService`：任务的增删改查与状态流转
- `Task` 实体：任务状态 `TODO → IN_PROGRESS → DONE` 的流转规则
- `application.yml` / `init.sql`：数据库配置与建表脚本
- `docs/ARCHITECTURE.md`：简要架构说明

演示用的 Bug 场景：`TaskService.getTaskDetail()` 在任务不存在时
不做判空，直接调用 `task.getStatus()`，会抛出
`NullPointerException`。你可以把下面的堆栈贴进「Bug 分析工作台」体验分析流程：

```
java.lang.NullPointerException: Cannot invoke "com.taskflow.entity.Task.getStatus()"
  at com.taskflow.service.TaskService.getTaskDetail(TaskService.java:52)
  at com.taskflow.controller.TaskController.getTask(TaskController.java:38)
```

> 再次说明：这些代码仅作为检索与问答的语料，永远不会被 RepoPilot 执行。

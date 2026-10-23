# TaskFlow 架构说明（演示数据）

TaskFlow 采用经典三层架构：

1. Controller 层：`UserController`、`TaskController`，负责接收请求与包装响应。
2. Service 层：`UserService`（注册/登录/密码哈希）、`TaskService`（任务 CRUD 与状态流转）。
3. Mapper 层：`UserMapper`、`TaskMapper`，负责数据库访问。

状态机：任务状态只有三条合法流转路径：
- TODO → IN_PROGRESS
- IN_PROGRESS → DONE
- 其他任何流转都会被 `TaskService.transition` 拒绝并抛出 409 异常。

已知问题：`TaskService.getTaskDetail` 未对 `findById` 的返回值判空，
任务不存在时会抛出 NullPointerException。

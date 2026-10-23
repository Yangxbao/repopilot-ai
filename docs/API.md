# API 接口说明

Base URL：`http://localhost:8080/api`。除认证接口外都需要请求头 `Authorization: Bearer <token>`。

统一响应结构（业务错误也返回 HTTP 200，用 code 区分；认证层缺 token 时返回 HTTP 401）：

```json
{ "code": 0, "message": "success", "data": { ... } }
```

常用错误码：40101 未登录 · 40301 无权限 · 40401 资源不存在 · 40001/40002 参数错误 ·
40901 冲突（用户名/库名重复）· 40902 库未就绪 · 41301 上传超限 · 40003 ZIP 非法 ·
42901 超过工具调用预算 · 50201 AI 调用失败 · 50202 AI 超时 · 50203 AI 未配置。

交互式文档：启动后访问 <http://localhost:8080/swagger-ui.html>（右上角 Authorize 填 token）。

## 认证

### POST /auth/register

```json
{ "username": "demo", "password": "secret66", "nickname": "演示" }
```

响应 `data`：`{ "token": "...", "expiresInHours": 72, "user": { "id":1, "username":"demo", "nickname":"演示", "role":"ADMIN" } }`
第一个注册的用户 role=ADMIN。用户名重复 → code 40901。

### POST /auth/login

```json
{ "username": "demo", "password": "secret66" }
```

用户名或密码错误统一返回 code 40101（不区分，防枚举）。

### GET /auth/me → 当前用户信息

## 代码库

### POST /repos/upload（multipart/form-data）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| file | file | ZIP 包（≤30MB；解压后单文件 ≤512KB、总 ≤30MB、≤5000 个条目） |
| name | text | 代码库名称（≤100 字符，同一用户内唯一） |
| description | text | 可选 |

返回创建的 RepoVO（status=PENDING）。非法 ZIP 返回 40003 并说明原因。

### POST /repos/import-sample
一键导入内置示例项目（幂等：已导入过则返回已有记录）。

### GET /repos?page=1&size=10 → 我的代码库分页列表

`data.list[]`：`{ id, name, description, status: "PENDING|INDEXING|READY|FAILED", fileCount, indexedCount, totalSize, errorMessage, isDemo, createdAt }`

### GET /repos/{id} → 详情 · DELETE /repos/{id} → 删除（连同索引与磁盘解压内容）

### GET /repos/{id}/tree → 文件树

```json
[ { "label": "src", "type": "directory", "size": 0, "children": [
      { "label": "App.java", "type": "file", "size": 320, "children": [] } ] } ]
```

### GET /repos/{id}/files?path=src/main/java/App.java → 文件内容（按行）

`data`：`{ path, language, lineCount, lines: ["...", "..."], content }`

### GET /repos/{id}/outline?path=… → 文件结构大纲（启发式）

`data.entries[]`：`{ "line": 40, "kind": "method", "text": "transition(Long taskId, String targetStatus)" }`

### GET /repos/{id}/search?q=TaskService&topK=10 → 关键词搜索

`data[]`：`{ fileId, path, language, score, matches: [ { "lineNo": 20, "text": "public User register(...)" } ] }`

### POST /repos/{id}/reindex → 重建索引 · 返回 status=INDEXING 的 RepoVO

## 代码问答

### POST /repos/{repoId}/qa

```json
{ "question": "用户注册流程在哪里实现？" }
```

响应 `data`：

```json
{
  "id": 1, "repoId": 1, "question": "...",
  "answer": "## 直接回答\n...（Markdown）",
  "evidence": [
    { "path": "src/main/java/com/taskflow/service/UserService.java",
      "startLine": 20, "endLine": 24,
      "snippet": "20: public User register(...)\n21: ...",
      "reason": "关键词检索命中（相关性得分 37）" } ],
  "provider": "mock", "model": "mock-template", "durationMs": 12,
  "status": "OK", "createdAt": "..."
}
```

检索不到依据时依然返回 200，但 answer 会明确说明"无法确定"且 evidence 为空——系统不会编造。
AI 服务失败时返回 code 50201/50202/50203，同时该问题会保存为 FAILED 记录。

### GET /qa?repoId=&page=1&size=10 → 问答历史 · GET /qa/{id} → 记录详情

## Bug 分析

### POST /repos/{repoId}/bug-analysis

```json
{
  "title": "查询任务详情报 NPE",
  "symptom": "GET /tasks/999 返回 500",
  "logContent": "java.lang.NullPointerException: ...\n  at com.taskflow.service.TaskService.getTaskDetail(TaskService.java:52)",
  "expectedResult": "返回 404 业务错误",
  "actualResult": "抛出 NullPointerException"
}
```

响应 `data.report`（BugAnalysisResult）：

```json
{
  "summary": "针对问题...识别到异常类型 NullPointerException...",
  "possibleCauses": [ { "cause": "...", "evidence": ["src/.../TaskService.java:52"], "confidence": "MEDIUM" } ],
  "troubleshootingSteps": [ { "priority": 1, "step": "定位异常抛出点", "detail": "搜索 NullPointerException..." } ],
  "openQuestions": ["错误是否可以稳定复现？..."],
  "testSuggestions": ["针对怀疑的代码路径编写/运行单元测试..."],
  "conclusion": "初步定位到 ... 处相关代码",
  "structured": true, "provider": "mock"
}
```

`structured=false` 表示 Remote 模式下模型未按 JSON 输出、已降级为纯文本。

### GET /bug-reports?repoId=&page= · GET /bug-reports/{id}

## AI 配置

### GET /ai-config → `{ mode, providerName, model, baseUrl, apiKeyConfigured, timeoutSeconds, maxRetries }`
（只暴露"是否已配置"，永不返回完整 Key）

### GET /ai-config/tools → 当前注册的 Agent 工具白名单 `{ "searchCode": "在已导入的代码库中..." , ... }`

### POST /ai-config/test → 连通性测试 `{ success, mode, latencyMs, reply | message }`

## 工作台 / 用户管理

### GET /dashboard/summary → `{ repoCount, fileCount, qaCount, bugReportCount, aiMode, recentQas[5], recentReports[5] }`

### GET /users?page=&size=（仅 ADMIN） · PUT /users/{id}/status `{ "status": "ACTIVE|DISABLED" }`（不能禁用自己）

## curl 快速示例

```bash
# 注册并登录
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"secret66"}'
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"secret66"}' | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).data.token))")

# 导入示例项目 → 轮询状态
curl -X POST http://localhost:8080/api/repos/import-sample -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/repos/1 -H "Authorization: Bearer $TOKEN"

# 提问
curl -X POST http://localhost:8080/api/repos/1/qa -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"question":"用户注册流程在哪里实现？"}'
```

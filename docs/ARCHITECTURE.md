# 系统架构（ARCHITECTURE）

## 1. 总体结构

RepoPilot AI 是一个**单体分层架构**的应用：一个 Spring Boot 后端 + 一个 Vue 3 前端。
刻意的单体设计：对学习者和个人部署者而言，"每个文件都能一次读完"比微服务更重要。

```
frontend (Vue3+TS+Vite+Element Plus)
    │  HTTP /api/*  （开发期 Vite 代理到 8080）
backend (Spring Boot 3.3, Java 21)
    ├── controller   接口层：参数校验、调用 Service、包装 ApiResponse
    ├── service      业务层：本项目的核心逻辑
    │    ├── index   导入与索引：ZipImporter（安全解压）→ IndexService（建索引）
    │    ├── search  检索抽象：SearchService 接口 + KeywordSearchService 实现
    │    ├── ai      AI 适配：AiProvider 接口 + Mock/Remote 实现 + Registry
    │    ├── agent   Agent：工具白名单 ToolRegistry + Bug 分析流水线
    │    └── qa      问答编排：检索 → 证据 → AI → 落库
    ├── mapper       MyBatis-Plus 数据访问（6 张表）
    ├── security     JWT 签发/校验、过滤器、SecurityUtils
    └── config       Security/MyBatis/异步线程池/OpenAPI/配置属性
```

## 2. 两条核心流程

### 2.1 代码导入与索引

```
用户上传 ZIP
  → RepoController.upload()
  → RepoService.createFromZip()
      1. 校验文件名后缀/非空
      2. 创建 repo 记录（status=PENDING）
      3. ZipImporter.extract()          ← 同步执行，非法包当场报错
           · 大小/条目数/压缩比限制
           · Zip Slip 路径穿越校验（..、绝对路径、盘符、NUL + canonical 复查）
           · 扩展名白名单 + 垃圾目录过滤 + 疑似密钥文件过滤
           · 文件头 8KB 二进制探测（PushbackInputStream，探测后回退字节）
      4. IndexService.buildIndexAsync() ← 异步（indexExecutor 线程池）
           读磁盘文本 → 写 file_index（路径/语言/行数/全文）→ repo=READY/FAILED
  → 立即返回 RepoVO(PENDING)，前端轮询状态
```

**设计点**：解压同步、建索引异步。用户能立刻知道 ZIP 是否非法，而不必等待索引；
索引失败体现在 repo.status=FAILED 与 error_message，不会丢响应。

### 2.2 问答（RAG）

```
用户提问
  → QaController.ask() → QaService.ask()
      1. RepoService.getOwned()            所有权检查（不属于则 404）
      2. status != READY 则拒绝
      3. KeywordSearchService.search()     检索 topK=6 个相关文件
      4. EvidenceFactory.fromHits()        转成证据（路径/行号/片段/依据）
      5. AiProvider.answerQuestion()       Mock=模板引用证据 / Remote=提示词→HTTP
      6. 失败：存 FAILED 记录 + 抛 BizException（AI 错误码）
         成功：qa_record 落库（含 evidence_json）→ 返回 QaVO
  → 前端渲染 Markdown 回答 + 证据卡片
```

### 2.3 Bug 分析（Agent 流水线）

```
BugAnalysisService.analyze()
  1. 插入 bug_report(status=RUNNING)
  2. ErrorInfoExtractor.extract()        纯 Java 正则提取：
     异常类名 / Caused by / 堆栈帧(类.方法(文件:行)) / HTTP 路径 / 高频关键词
  3. buildSearchQueries()                构造检索词（类名 > 异常 > 文件名 > 关键词）
  4. 循环 ≤4 轮 toolRegistry.execute("searchCode", ...)   ← 每次调用写 tool_call_log
  5. searchDocuments 补充 1 轮（md/yml/sql/properties）
  6. 证据去重合并（≤8 条）→ toolRegistry.execute("analyzeIssue", ...)
     → AiProvider.analyzeBug()           Remote 要求 JSON Schema 输出，解析失败降级纯文本
  7. bug_report 更新为 DONE + report_json；任何异常 → FAILED + 错误信息
```

为什么用**固定流水线**而不是让模型自由决定调哪些工具：
可复现、可审计、成本可控（每轮检索都有日志，预算封顶 8 次）。
模型自主的 ReAct 循环列入 ROADMAP，且会继续复用同一套工具白名单与预算机制。

## 3. AI 适配层

```
AiProvider (接口)
   ├── getName()/getModelName()/isMock()
   ├── answerQuestion(ctx) → Markdown
   ├── analyzeBug(ctx) → BugAnalysisResult
   └── ping()
        ├── MockAiProvider     零配置；模板生成文字，但引用真实检索证据
        └── RemoteAiProvider   OpenAI 兼容 /chat/completions
             · RestClient + SimpleClientHttpRequestFactory（连接 10s / 读取 60s）
             · 重试：网络错误与 5xx，最多 maxRetries 次，指数退避；4xx 立即失败
             · 所有失败包装为 AiProviderException(ErrorCode)
AiProviderRegistry   按 repopilot.ai.provider 配置选择实现；非法值回退 mock
```

## 4. 安全模型（详见 SECURITY.md）

- **认证**：JWT 无状态（Authorization: Bearer），BCrypt 密码哈希；
  第一个注册用户自动成为 ADMIN。
- **授权**：所有按 ID 的资源访问经 `RepoService.getOwned()`；查无权限统一返回 404（防资源枚举）；
  ADMIN 接口用 `@PreAuthorize("hasRole('ADMIN')")` 后端强制。
- **Agent 工具**：白名单 + 每次运行预算（次数/截止时间）+ 全量调用日志 + 只读工具，
  只能访问数据库中的已索引内容，**没有任何文件系统/命令执行能力**。

## 5. 关键技术决策记录

| 决策 | 原因 |
| --- | --- |
| 不用 Spring AI，自研 AiProvider | 依赖更少、行为可控、便于初学者读懂每一行；接口可随时替换为 Spring AI |
| 文件全文存 DB（file_index.content） | MVP 最简可靠；规模上限由导入限制（30MB/库）保证，ROADMAP 中再演进为倒排/向量索引 |
| 解压同步 + 索引异步 | 用户即时得到 ZIP 校验反馈；耗时索引不阻塞请求线程 |
| 二进制探测用 PushbackInputStream | 早期版本预读 8KB 后直接丢失了文件内容（单元测试抓到的真实 bug），回退字节修复 |
| Mock 模式坚持"模板 + 真实证据" | 演示完整链路的同时，绝不把模板输出冒充模型推理 |

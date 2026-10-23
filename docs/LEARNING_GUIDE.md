# 学习指南（LEARNING_GUIDE）—— 写给 Java 初学者

这个项目是刻意为你写的：代码里没有炫技，每层职责单一，注释解释"为什么"而不是"是什么"。
建议按下面的顺序读，最后跟着**一条完整调用链**走一遍，你会把 Spring Boot 的骨架彻底看懂。

## 1. 阅读顺序（由外到内）

1. **`backend/pom.xml`** —— 认识 Maven：parent、依赖、`<java.version>21`。
   问自己：为什么 mysql 是 runtime scope？H2 为什么也在？
2. **`application.yml`**（resources 下）—— Spring 的配置面板。
   注意 `${REPOPILOT_DB_PASSWORD:}` 这种"环境变量:默认值"写法，密钥就是这样进来的。
3. **`RepoPilotApplication.java`** —— 20 行的启动类；`@MapperScan` 是 MyBatis-Plus 的入口。
4. **`common/` 包** —— 统一响应 `ApiResponse`、错误码 `ErrorCode`、`BizException`、
   `GlobalExceptionHandler`。理解"为什么异常要统一处理"：Controller 里几乎看不到 try-catch。
5. **`entity/` + `mapper/`** —— 6 张表 6 个实体。看 `User.java` 与 `schema-mysql.sql` 的 sys_user
   对照，理解 `map-underscore-to-camel-case`（password_hash ↔ passwordHash）。
6. **`security/` 包** —— `JwtService`（签发/校验）→ `JwtAuthFilter`（每个请求过一遍）→
   `SecurityUtils`（业务层取当前用户）。再看 `config/SecurityConfig` 里"哪些路径放行"。
7. **`controller/AuthController` + `service/AuthService`** —— 最小完整闭环：注册/登录。
   留意 BCrypt 和"第一个用户是管理员"。
8. **`service/index/ZipImporter`** —— 全项目最有价值的安全代码，配合 docs/SECURITY.md 读。
9. **`service/search/KeywordSearchService`** —— 检索打分，纯 Java，无框架魔法，适合逐行读。
10. **`service/ai/`** —— 接口 + 两个实现 + 注册器，这是"策略模式"的教科书案例。
11. **`service/qa/QaService` + `service/agent/`** —— 编排层：把前面的积木串成业务。
12. **前端 `frontend/src/`** —— `api/http.ts`（拦截器怎么统一解包/处理 401）→
    `stores/auth.ts` → `router/index.ts`（路由守卫）→ 任选一个 View 看页面怎么调 API。

## 2. 必走的一条完整调用链：代码问答

> 场景：你在问答页输入"TaskController 调用了哪些 Service？"点击提问。

### ① Vue 页面（frontend/src/views/QaView.vue）

```ts
asking.value = true
current.value = await qaApi.ask(repoId.value, q)   // ← 发起请求
```

- `QaView` 是"对话区 + 证据区"分栏的页面组件；`<script setup>` 是 Vue 3 组合式 API。
- 提交后进入 loading 态；成功渲染 Markdown 回答与 `EvidenceList` 证据卡片；
  失败把错误放进一条 FAILED 消息展示——**页面永远有真实状态反馈**。

### ② API 请求（frontend/src/api/qa.ts + api/http.ts）

```ts
ask: (repoId, question) => http.post(`/repos/${repoId}/qa`, { question })
```

- `http.ts` 的 axios 实例：请求拦截器自动加 `Authorization: Bearer <token>`（token 存在 Pinia + localStorage）；
  响应拦截器解包 `{code,message,data}`，code≠0 抛错并弹提示，401 清登录态回登录页。
- 请求 URL 是 `/api/...`，由 Vite 代理转发到 8080（`vite.config.ts`）。

### ③ Controller（backend/.../controller/QaController.java）

```java
@PostMapping("/api/repos/{repoId}/qa")
public ApiResponse<QaVO> ask(@PathVariable long repoId, @Valid @RequestBody QaRequest request) {
    return ApiResponse.ok(qaService.ask(repoId, request, SecurityUtils.currentUserId()));
}
```

- Controller 只做三件事：接参数（`@Valid` 触发 Bean Validation）、取当前用户、委托 Service。
- 请求体先被 `QaRequest` record 校验（2-1000 字符），不合法会被
  `GlobalExceptionHandler` 转成 code 40002。

### ④ Service：QaService.ask()（backend/.../service/qa/QaService.java）

业务编排的顺序（对应源码逐行看）：

```java
Repo repo = repoService.getOwned(repoId, userId);      // (a) 所有权检查，非我所有 → 404
if (!"READY".equals(repo.getStatus())) throw ...;      // (b) 索引没完成 → 40902
List<SearchHit> hits = searchService.search(...);      // (c) 检索
List<EvidenceVO> evidence = EvidenceFactory.fromHits(hits); // (d) 证据
String answer = providerRegistry.active().answerQuestion(...); // (e) AI
qaRecordMapper.insert/updateById(...);                 // (f) 持久化
```

### ⑤ 检索服务（service/search/KeywordSearchService.java）

`(c)` 展开后：分词 `tokenize()` → 遍历该库文件 `scoreFile()`（内容词频 + 路径命中 + 短语奖励）→
排序取 topK。**这一层没有任何 Spring 复杂度，是纯算法**，也是最适合你写单元测试来学习的地方
（测试就摆在 `KeywordSearchServiceTest`，改一个系数跑一遍看效果）。

### ⑥ AI Provider（service/ai/）

`(e)` 展开后：`AiProviderRegistry.active()` 按配置返回 Mock 或 Remote 实现——
上层代码完全不知道背后是谁，这就是**面向接口编程 + 策略模式**。
- Mock：`MockAiProvider.answerQuestion()` 直接用证据拼 Markdown（注意看它如何如实标注模拟模式）；
- Remote：`RemoteAiProvider` 用 RestClient 发 OpenAI 兼容请求，含超时/重试/错误包装。

### ⑦ 数据持久化（entity/QaRecord + mapper/QaRecordMapper）

`(f)`：MyBatis-Plus 的 `BaseMapper.insert()`——你没有写一行 SQL，
但看 `QaService.saveRecord()` 如何组装字段，以及 `evidence_json` 是用 Jackson 序列化存的。
打开数据库（或 H2 模式下看 `backend/data/repopilot-h2.mv.db`）能看到这行记录。

### ⑧ 回到页面

Controller 返回 `ApiResponse<QaVO>` → 拦截器解包出 QaVO → `QaView` 把 answer 渲染成 Markdown、
把 evidence 渲染成证据卡片 → 你点击某条证据的路径，可以跳到「代码库详情」核对原文——**闭环完成**。

### 这条链上的设计要点（面试可以讲）

1. 分层职责：Controller 不写业务，Service 不碰 HTTP，Mapper 不懂业务。
2. 统一响应 + 全局异常：业务代码用抛异常表达失败。
3. 面向接口：SearchService / AiProvider 都可替换实现。
4. 横切关注点：认证在过滤器、授权在 Service、审计在工具日志，都不污染业务方法。

## 3. 动手练习（建议按顺序做）

1. **改打分**：把路径命中权重从 +10 改成 +5，跑 `mvn test`，看 KeywordSearchServiceTest
   的 `pathMatchBoost` 会不会还过；想一个更合理的权重。
2. **加一个白名单扩展名**：让 `.proto` 文件也能被索引（改 `ZipImporter.buildExtLanguage()`），
   并在 ZipImporterTest 加一个用例。
3. **给问答加"最近 N 条上下文"**：把同库上一条问答的 question 一起放进 prompt
   （改 `QaService` 与 `RemoteAiProvider.renderQaPrompt`），观察回答质量变化。

做完这三个练习，你就摸过：检索算法、导入白名单、RAG 提示词——正是这个项目的三根支柱。

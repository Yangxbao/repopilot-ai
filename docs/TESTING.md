# 测试报告（TESTING）

本文只记录**实际执行过**的测试与结果。以下所有结果都来自本机真实运行（Windows 11，JDK 21.0.11 / Maven 3.9.4）。

## 1. 后端测试

执行命令：

```bash
cd backend
mvn test
```

最近一次执行结果（2026-10-24，CORS 调整后复跑）：

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- AiFailureIntegrationTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- FullFlowIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- AiProviderTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- ZipImporterTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- KeywordSearchServiceTest
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

测试基础设施：JUnit 5 + Spring Boot Test + MockMvc + H2 内存库（MySQL 兼容模式）+ Mock AI。
配置：`backend/src/test/resources/application-test.yml`。

### 覆盖明细

**ZipImporterTest（7 个）— 上传安全**
- `../` 路径穿越条目被拒绝（Zip Slip）
- 绝对路径 / 盘符条目被拒绝
- 非 ZIP 文件报 BAD_ZIP
- 超过单文件 512KB 限制被拒绝
- 超高压缩比（100KB 全 0 → ~200B）被拒绝
- 正常项目解压成功且白名单文本保留（含内容校验）
- .git / node_modules / target / .env / 二进制 PNG 全部被过滤

**KeywordSearchServiceTest（5 个）— 检索打分**
- 中英混合分词（`UserController 注册流程 register`）
- 内容命中返回正确行号
- 路径命中加权（文件名匹配得分更高）
- 无命中返回 null（不编造结果）
- 空内容/空查询边界处理

**AiProviderTest（6 个）— AI 模式**
- Mock 问答引用真实检索路径且标注模拟模式
- Mock 无证据时如实说"无法确定"
- Mock Bug 分析输出结构化报告
- Remote 未配置 Key 快速失败（AI_NOT_CONFIGURED）
- Remote 服务不可达超时失败（AI_TIMEOUT，应用不崩溃）
- Remote 返回非法 JSON 时降级 structured=false

**AiFailureIntegrationTest（1 个）— 故障降级（@SpringBootTest）**
- 注入必然失败的 Provider（remote 模式）：问答接口返回 50202、FAILED 记录落库、
  连通性测试返回 success=false——端到端验证"模型挂了应用不崩"。

**FullFlowIntegrationTest（11 个）— 全链路集成（真实走 HTTP 层）**
按 `@Order` 串联：注册 → 重复注册/错误密码/弱密码被拒 → 登录 + 导入示例项目 → 轮询至 READY →
文件树与文件内容 → 关键词搜索（命中带行号）→ Mock 问答（证据非空、answer 标注模拟模式、记录可查）→
Bug 分析（NPE 堆栈 → DONE 报告、排查步骤、引用 TaskService.java）→ 上传真实 ZIP 建库并索引（fileCount=2）→
用户 B 访问用户 A 的库返回 40401（详情/文件/提问三处）→ 未登录 401 → 删除后资源 404。

## 2. 前端构建检查

```bash
cd frontend
npm run build        # = vue-tsc --noEmit && vite build
```

最近一次执行结果：

```text
vite v6.4.5 building for production...
✓ built in 9.65s
（vue-tsc --noEmit 无类型错误）
```

## 3. 运行时冒烟验证（真实 jar + 真实浏览器）

**API 冒烟（jar 启动 + H2 模式，10/10 通过）**：
注册 → 登录 → 导入示例 → 索引就绪(17 文件) → 文件树 → 搜索(命中 TaskController.java L5) →
Mock 问答(1 条证据、引用 .java 路径) → Bug 分析(DONE、4 步骤、3 原因) → Dashboard 统计 → 未登录 401。

**浏览器端到端（ZCode 内置 Chromium，1440×900）**：
注册登录 → 工作台渲染 → 导入示例 → 文件树/搜索/代码高亮/文件大纲 → 问答页提问并渲染 Mock 回答与证据 →
Bug 分析页提交堆栈并生成报告 → 登录页/工作台/详情/搜索/问答/报告共 5 张截图存入 `docs/screenshots/`。

**CI**：`.github/workflows/ci.yml` 在每次 push/PR 时用 JDK 21 跑 `mvn test`、Node 22 跑 `npm run build`。

## 4. 开发过程中测试抓到并修复的真实缺陷

记录在这里，证明测试的价值，也方便学习者理解：

1. **ZIP 二进制探测吞内容**（ZipImporterTest 抓到）：`looksBinary()` 预读 8KB 后未回退，
   正式复制时文件内容已被消费，解压结果全是空文件 → 改用 `PushbackInputStream.unread()`。
2. **sourceDir 相对路径不一致**（jar 冒烟抓到，测试未暴露）：`./data/...` 未 normalize，
   与 `normalize()` 后的写入路径 `startsWith` 比较失败，示例项目文件被静默跳过 → 统一 normalize。
3. **问答记录未回填答案**（集成测试审查时发现）：记录先插入、答案设置后未 updateById → 历史里答案为空。
4. **前端 axios 未解包 ApiResponse**（浏览器验证抓到）：拦截器返回整个 AxiosResponse，
   `res.user` 为 undefined 抛错 → 拦截器解包 `body.data`。
5. **CORS 端口漂移被拒**（浏览器验证抓到）：Vite 端口被占切到 5174 后，
   固定白名单 5173 导致浏览器 POST 被 Spring 拒绝（Invalid CORS request）→ 改用 originPatterns。

## 5. 未做的事（如实说明）

- 未做单元测试覆盖率统计（可加 JaCoCo）。
- 未做前端组件级测试（Vitest/Playwright）——当前以 vue-tsc 类型检查 + 构建检查 + 浏览器手工/自动化冒烟代替。
- 未做压力测试；检索性能结论来自小规模库（17~几百文件）的实测感受，不是基准测试数据。

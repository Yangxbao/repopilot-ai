# 安全说明（SECURITY）

RepoPilot 的定位决定了它要处理三类不可信输入：**用户上传的 ZIP**、**用户/模型构造的检索参数**、
**导入的第三方代码内容**。以下是每个攻击面的对策与仍然存在的风险。

## 1. 上传安全（ZIP 是最大的攻击面）

| 风险 | 对策 | 代码位置 |
| --- | --- | --- |
| ZIP 路径穿越（Zip Slip，写入任意文件） | 拒绝 `..` 段、绝对路径、盘符（`:`）、NUL、路径层级 >30；解压目标做 canonical/normalize 二次校验 | `ZipImporter.validateEntryName()` + extract 中的 `target.startsWith(destDir)` |
| 压缩炸弹（解压后超大） | 单包 ≤30MB、条目 ≤5000、单文件 ≤512KB、解压总量 ≤30MB、压缩比 >500 拒绝；条目声明大小与实际复制大小双重校验 | `ZipImporter` + `repopilot.upload.*` 配置 |
| 超大上传流 | Spring multipart 上限 50MB；流式落临时文件时带硬上限复制 | `application.yml` + `copyBounded()` |
| 恶意/损坏 ZIP | `ZipFile` 解析失败（含加密包）→ BAD_ZIP，友好报错 | `ZipImporter.extract()` |
| 垃圾/危险内容入库 | 扩展名白名单（java/xml/yml/json/md/sql/js/ts/html/css/…）+ 目录黑名单（.git/node_modules/target/build/dist/…）+ 疑似密钥文件名（.env、id_rsa、*.pem 等）一律跳过 + `.min.js`/`.log`/`.lock`/`.map` 跳过 | `ZipImporter` 静态规则表 |
| 二进制伪装成文本 | 文件头 8KB 出现 NUL 字节即跳过；探测用 PushbackInputStream 回退字节，避免内容丢失 | `ZipImporter.extract()` |
| **执行导入的代码** | **导入流程只读文件内容，从不执行任何脚本/程序/命令**；示例项目也仅是检索语料 | 架构性保证 |

上传失败的 ZIP：已创建的 repo 记录会被删除，避免残留半成品。

## 2. 认证与授权

- 密码只存 **BCrypt 哈希**；登录失败统一提示"用户名或密码错误"（不泄露用户名是否存在）。
- JWT（HS256 + 至少 32 字节密钥）。生产必须配置 `REPOPILOT_JWT_SECRET`；
  未配置时启动会生成随机开发密钥并打 WARN（重启后登录态失效）。
- 资源所有权：所有按 ID 的访问都过 `RepoService.getOwned()`；无权访问**统一返回 404**（不是 403），
  防止通过响应差异枚举他人资源 ID。有集成测试覆盖跨用户访问。
- ADMIN 接口（用户管理）用 `@PreAuthorize("hasRole('ADMIN')")` 在**后端**强制；
  前端隐藏入口只是体验优化。禁用用户后其 token 仍有效至过期——如需即时踢出，可加 Redis 黑名单（ROADMAP）。

## 3. Agent 工具安全（模型永远拿不到系统能力）

- **白名单**：`ToolRegistry` 只注册 5 个只读工具；未注册的工具调用被拒绝并记日志。
- **只读、零文件系统**：工具操作的数据全部来自 `file_index` 表；`readFile` 的 path 必须精确匹配
  已索引路径，且服务端再走一次 `normalizePath()`（拒绝 `..`、超长路径）。
  不存在"传绝对路径读电脑文件"的可能，也没有任何 Shell/进程执行入口。
- **权限纵深**：每个工具执行时都再次校验 repo 所有权。
- **预算与审计**：单次分析 ≤8 次工具调用 + 120s 截止；每次调用写 `tool_call_log`（参数、摘要、耗时）。
- **AI 输出当数据看**：模型返回的内容只作为展示文本/JSON 解析，从不进入任何执行路径；
  前端用 markdown-it 渲染时 `html: false`，模型输出中的 HTML 会被转义，防止注入。

## 4. 密钥与敏感信息管理

- API Key 只通过环境变量（`REPOPILOT_AI_API_KEY`）进入进程；不落库、不进 Git、不打日志。
- `/ai-config` 接口只返回 `apiKeyConfigured` 布尔值，永不返回完整 Key。
- 数据库密码通过 `REPOPILOT_DB_PASSWORD` 等环境变量注入，`application.yml` 中只有占位符。
- `.gitignore` 排除 `.env*`、`backend/data/`（解压内容与 H2 库文件）、日志。
- 未捕获异常统一处理：对外只返回通用错误信息，完整堆栈只进服务端日志。

## 5. 前端与传输

- 开发模式 `/api` 走 Vite 代理（同源）；后端 CORS 仅放行 `http://localhost:*` /
  `http://127.0.0.1:*`（开发便利，端口漂移不会被拦），生产部署建议前后端同源或收敛白名单。
- XSS：AI 回答 markdown-it（html:false）渲染；代码内容按纯文本渲染；Vue 默认对插值转义。
- 生产部署必须走 HTTPS（JWT 明文传输在 HTTP 下可被窃听）。

## 6. 坦白的剩余风险（已知 & 接受 / 待改进）

1. JWT 无法主动吊销：禁用用户后 token 在过期前仍可用（改用短有效期 + 刷新令牌/黑名单可解）。
2. 检索/读取在应用内存中进行，恶意巨型查询受参数长度限制（query ≤200 字符）保护，但更大的库
   需要真正倒排索引以避免内存压力。
3. 无请求级限流：登录/注册接口可被暴力尝试（可加 Bucket4j 或网关限流）。
4. H2 演示模式的库文件在本地明文存储，仅用于体验。
5. tool_call_log 的 params_json 截断到 2000 字符，超大参数只保留头部（记录不完整但不影响安全）。

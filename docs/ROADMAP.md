# 后续路线（ROADMAP）

按优先级排列；"✅ 可行性"指在当前架构上的改造难度。

## 检索与 RAG

1. **语义检索（Embedding）** ★核心
   - `AiProvider` 增加 `embed(List<String>)`；新增 `EmbeddingSearchService implements SearchService`
   - `file_index` 增加向量列或引入独立向量库（pgvector / Milvus / Qdrant）
   - 前端问答页显示检索模式开关（关键词 / 语义 / 混合）
   - 可行性：高（SearchService 接口已预留）
2. **混合检索与重排**：关键词 + 向量两路召回，RRF 融合，可用 Remote 模型做 LLM rerank
3. **上下文压缩**：命中文件先做函数级切分（配合更精确的 Java 解析器 javaparser），减少噪音
4. **导入增强**：Git URL 直接克隆导入（只读）、多 ZIP 分卷、目录监听增量更新

## Agent

5. **模型驱动的 ReAct 循环**：让模型在白名单内自主决定下一步调用哪个工具
   （复用现有 ToolRegistry 白名单、预算、日志——安全边界不变）
6. **流式输出**：SSE 替代一次性返回，回答逐字上屏
7. **交互式排查**：报告中的"仍需确认"可一键补充信息触发二次分析
8. **自动修复建议（只生成 diff 不应用）**：与"分析"严格分离，由用户手动 review

## 工程化

9. **测试补强**：JaCoCo 覆盖率报告、前端 Vitest 组件测试、Playwright E2E 入 CI
10. **认证增强**：刷新令牌 + 登出黑名单（解决"禁用用户后 token 仍有效"）、登录限流
11. **部署**：后端/前端 Dockerfile + 完整 compose（含应用本体）、HTTPS 反向代理示例
12. **性能**：Element Plus 按需引入、file_index 倒排表或 FULLTEXT（ngram）索引、检索分页
13. **多语言检索**：更细的分词（IK / jieba 类）改善中文召回

## 界面

14. 证据点击直达源码行（当前是整文件视图，可加行锚点高亮）
15. 问答支持多轮会话（当前每问独立）
16. 深色主题

---

**不建议做的**：微服务拆分、多级缓存、消息队列——当前规模下收益为负，学习负担为正。
等真实需求出现再说。

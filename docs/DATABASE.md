# 数据库设计（DATABASE）

MySQL 8（生产）/ H2 MySQL 模式（演示与测试）。建表脚本：
`backend/src/main/resources/db/schema-mysql.sql` 与 `schema-h2.sql`（结构一致，类型/索引语法不同）。

## 1. ER 关系图

```
   sys_user (1) ────────< (N) repo
                             │ 1
                             └────────< (N) file_index
   sys_user (1) ────────< (N) qa_record      >─── (1) repo
   sys_user (1) ────────< (N) bug_report     >─── (1) repo
   sys_user (1) ────────< (N) tool_call_log  >─── (1) repo
                                    │
                                    └─ biz_id → qa_record.id 或 bug_report.id
                                       （biz_type 区分，逻辑关联，不加外键）
```

说明：为保持 MVP 简洁，跨表关联使用 user_id / repo_id 逻辑外键（不建 FK 约束），
由 Service 层保证一致性；唯一约束与普通索引在数据库层强制。

## 2. 表结构

### sys_user —— 用户

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AUTO | 主键 |
| username | VARCHAR(50) UNIQUE | 登录名，`^[a-zA-Z0-9_]{3,32}$` |
| password_hash | VARCHAR(100) | BCrypt 哈希（永不返回给前端） |
| nickname | VARCHAR(50) | 展示昵称 |
| role | VARCHAR(20) | USER / ADMIN（第一个注册用户为 ADMIN） |
| status | VARCHAR(20) | ACTIVE / DISABLED |
| created_at / updated_at | DATETIME | 时间戳（DB 默认维护） |

### repo —— 代码库

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 所有者（所有权检查的依据） |
| name | VARCHAR(100) | 名称；**UNIQUE(user_id, name)** |
| description / original_filename | VARCHAR | 描述 / 上传的 ZIP 文件名 |
| status | VARCHAR(20) | PENDING → INDEXING → READY / FAILED |
| file_count / indexed_count | INT | 导入文件数 / 已索引数（前端轮询展示进度） |
| total_size | BIGINT | 文本文件总字节 |
| error_message | VARCHAR(1000) | FAILED 时的原因 |
| is_demo | TINYINT(1) | 1 = 内置示例项目（前端打"演示数据"标签） |

索引：`uk_repo_user_name(user_id,name)` 唯一；`idx_repo_user_created(user_id,created_at)` 支撑"我的列表"分页。

### file_index —— 文件索引（检索数据来源）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| repo_id | BIGINT | 所属代码库 |
| path | VARCHAR(500) | ZIP 内相对路径（/ 分隔）；**UNIQUE(repo_id, path)** |
| ext / language | VARCHAR | 扩展名 / 语言标签（Java/YAML/…） |
| size / line_count | BIGINT / INT | 字节数 / 行数 |
| content | MEDIUMTEXT（H2: CLOB） | 文件全文（仅安全白名单内的文本文件） |

索引：`uk_file_repo_path(repo_id,path)` 唯一（防重复导入）；`idx_file_repo(repo_id)`。
路径索引宽度：500×4B(utf8mb4)+8B ≈ 2008B < InnoDB 3072B 上限，安全。

### qa_record —— 问答记录

question（问题原文）、answer（Markdown 回答）、evidence_json（EvidenceVO[] 序列化）、
provider/model（AI 模式与模型名）、duration_ms、status（OK/FAILED）、error_message。
索引：`idx_qa_user_repo(user_id,repo_id,created_at)` —— "我的历史"与"某库历史"都走这个组合索引。

### bug_report —— Bug 分析报告

title / symptom / log_content / expected_result / actual_result（原始输入，日志可能很大 → MEDIUMTEXT）、
report_json（BugAnalysisResult 结构化序列化）、status（DONE/FAILED）、provider/model/duration_ms。
索引：`idx_bug_user_repo(user_id,repo_id,created_at)`。

### tool_call_log —— Agent 工具调用日志（审计）

biz_type（QA/BUG_ANALYSIS）+ biz_id、tool_name、params_json、result_summary、success、
error_message、duration_ms。
索引：`idx_tool_user_repo(user_id,repo_id,created_at)`。
用途：回答"这次分析到底看了哪些代码"，也是排查分析质量的第一现场。

## 3. 索引设计思路

1. **所有列表查询都带 user_id 前缀过滤**，组合索引把 user_id 放最左，分页 + 排序（created_at）都在索引内完成。
2. **唯一约束承担防重职责**：用户名、(用户,库名)、(库,文件路径) —— 应用层还会先查一次给友好报错，数据库唯一键兜底并发。
3. content 列**故意不建全文索引**：MVP 检索在 Java 内存中完成打分（见 RAG_AND_AGENT.md），
   规模由导入限制封顶；引入 FULLTEXT/向量索引属于 ROADMAP 演进项。

## 4. 数据生命周期

- 删除代码库：事务性地删除 file_index 与 repo 行 + 清理磁盘解压目录 + 问答/报告/工具日志**保留**
  （审计价值高于级联删除；如需级联可在 ROADMAP 中做成可选项）。
- H2 演示模式数据落盘 `backend/data/repopilot-h2.mv.db`，删除该目录即重置。

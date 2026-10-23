-- =====================================================================
-- RepoPilot AI - MySQL 8 建表脚本（utf8mb4）
-- 执行方式：mysql -u root -p < backend/src/main/resources/db/schema-mysql.sql
-- 或者执行：CREATE DATABASE repopilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- =====================================================================

CREATE DATABASE IF NOT EXISTS repopilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE repopilot;

-- 用户表：登录账号、角色（USER / ADMIN）、状态（ACTIVE / DISABLED）
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(50)  NOT NULL COMMENT '登录名，唯一',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，不存明文',
    `nickname`      VARCHAR(50)  NULL,
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

-- 代码库表：一次 ZIP 导入对应一条记录
CREATE TABLE IF NOT EXISTS `repo` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT        NOT NULL COMMENT '所有者',
    `name`              VARCHAR(100)  NOT NULL,
    `description`       VARCHAR(500)  NULL,
    `original_filename` VARCHAR(255)  NULL COMMENT '上传的 ZIP 文件名',
    `status`            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / INDEXING / READY / FAILED',
    `file_count`        INT           NOT NULL DEFAULT 0 COMMENT '导入的文本文件数',
    `indexed_count`     INT           NOT NULL DEFAULT 0 COMMENT '已索引文件数',
    `total_size`        BIGINT        NOT NULL DEFAULT 0 COMMENT '文本文件总字节数',
    `error_message`     VARCHAR(1000) NULL,
    `is_demo`           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = 内置演示数据',
    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_repo_user_name` (`user_id`, `name`),
    KEY `idx_repo_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '导入的代码库';

-- 文件索引表：保存文本文件全文，关键词检索的数据来源
CREATE TABLE IF NOT EXISTS `file_index` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `repo_id`    BIGINT       NOT NULL,
    `path`       VARCHAR(500) NOT NULL COMMENT 'ZIP 内相对路径，统一使用 /',
    `ext`        VARCHAR(20)  NULL,
    `language`   VARCHAR(20)  NULL,
    `size`       BIGINT       NOT NULL DEFAULT 0,
    `line_count` INT          NOT NULL DEFAULT 0,
    `content`    MEDIUMTEXT   NULL COMMENT '文件全文（仅文本文件）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_repo_path` (`repo_id`, `path`),
    KEY `idx_file_repo` (`repo_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件索引';

-- 问答记录表
CREATE TABLE IF NOT EXISTS `qa_record` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `repo_id`       BIGINT        NOT NULL,
    `user_id`       BIGINT        NOT NULL,
    `question`      VARCHAR(2000) NOT NULL,
    `answer`        MEDIUMTEXT    NULL COMMENT 'AI 回答（Markdown）',
    `evidence_json` MEDIUMTEXT    NULL COMMENT '证据引用列表 JSON',
    `provider`      VARCHAR(30)   NOT NULL,
    `model`         VARCHAR(100)  NULL,
    `duration_ms`   INT           NOT NULL DEFAULT 0,
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'OK' COMMENT 'OK / FAILED',
    `error_message` VARCHAR(1000) NULL,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qa_user_repo` (`user_id`, `repo_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '问答记录';

-- Bug 分析报告表
CREATE TABLE IF NOT EXISTS `bug_report` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `repo_id`        BIGINT        NOT NULL,
    `user_id`        BIGINT        NOT NULL,
    `title`          VARCHAR(200)  NOT NULL,
    `symptom`        VARCHAR(4000) NULL COMMENT '错误现象',
    `log_content`    MEDIUMTEXT    NULL COMMENT '日志 / 堆栈原文',
    `expected_result` VARCHAR(2000) NULL,
    `actual_result`  VARCHAR(2000) NULL,
    `report_json`    MEDIUMTEXT    NULL COMMENT '结构化分析报告 JSON',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'DONE' COMMENT 'DONE / FAILED',
    `provider`       VARCHAR(30)   NULL,
    `model`          VARCHAR(100)  NULL,
    `duration_ms`    INT           NOT NULL DEFAULT 0,
    `error_message`  VARCHAR(1000) NULL,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_bug_user_repo` (`user_id`, `repo_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Bug 分析报告';

-- Agent 工具调用日志表：每次工具调用都会留痕，便于审计与排查
CREATE TABLE IF NOT EXISTS `tool_call_log` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT        NOT NULL,
    `repo_id`        BIGINT        NOT NULL,
    `biz_type`       VARCHAR(20)   NOT NULL COMMENT 'QA / BUG_ANALYSIS',
    `biz_id`         BIGINT        NULL COMMENT '关联的问答或报告 ID',
    `tool_name`      VARCHAR(50)   NOT NULL,
    `params_json`    VARCHAR(2000) NULL,
    `result_summary` VARCHAR(1000) NULL,
    `success`        TINYINT(1)    NOT NULL DEFAULT 1,
    `error_message`  VARCHAR(1000) NULL,
    `duration_ms`    INT           NOT NULL DEFAULT 0,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tool_user_repo` (`user_id`, `repo_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 工具调用日志';

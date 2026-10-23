-- =====================================================================
-- RepoPilot AI - H2（MySQL 兼容模式）建表脚本
-- 用于：--spring.profiles.active=h2 零配置演示模式，以及自动化测试。
-- 与 schema-mysql.sql 结构一致；差异仅是类型与索引语法。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS repo (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    name              VARCHAR(100)  NOT NULL,
    description       VARCHAR(500)  NULL,
    original_filename VARCHAR(255)  NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    file_count        INT           NOT NULL DEFAULT 0,
    indexed_count     INT           NOT NULL DEFAULT 0,
    total_size        BIGINT        NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000) NULL,
    is_demo           TINYINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_repo_user_name UNIQUE (user_id, name)
);
CREATE INDEX IF NOT EXISTS idx_repo_user_created ON repo (user_id, created_at);

CREATE TABLE IF NOT EXISTS file_index (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    repo_id    BIGINT       NOT NULL,
    path       VARCHAR(500) NOT NULL,
    ext        VARCHAR(20)  NULL,
    language   VARCHAR(20)  NULL,
    size       BIGINT       NOT NULL DEFAULT 0,
    line_count INT          NOT NULL DEFAULT 0,
    content    CLOB         NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_file_repo_path UNIQUE (repo_id, path)
);
CREATE INDEX IF NOT EXISTS idx_file_repo ON file_index (repo_id);

CREATE TABLE IF NOT EXISTS qa_record (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    repo_id       BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    question      VARCHAR(2000) NOT NULL,
    answer        CLOB          NULL,
    evidence_json CLOB          NULL,
    provider      VARCHAR(30)   NOT NULL,
    model         VARCHAR(100)  NULL,
    duration_ms   INT           NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL DEFAULT 'OK',
    error_message VARCHAR(1000) NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_qa_user_repo ON qa_record (user_id, repo_id, created_at);

CREATE TABLE IF NOT EXISTS bug_report (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    repo_id         BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    symptom         VARCHAR(4000) NULL,
    log_content     CLOB          NULL,
    expected_result VARCHAR(2000) NULL,
    actual_result   VARCHAR(2000) NULL,
    report_json     CLOB          NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DONE',
    provider        VARCHAR(30)   NULL,
    model           VARCHAR(100)  NULL,
    duration_ms     INT           NOT NULL DEFAULT 0,
    error_message   VARCHAR(1000) NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bug_user_repo ON bug_report (user_id, repo_id, created_at);

CREATE TABLE IF NOT EXISTS tool_call_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    repo_id        BIGINT        NOT NULL,
    biz_type       VARCHAR(20)   NOT NULL,
    biz_id         BIGINT        NULL,
    tool_name      VARCHAR(50)   NOT NULL,
    params_json    VARCHAR(2000) NULL,
    result_summary VARCHAR(1000) NULL,
    success        TINYINT       NOT NULL DEFAULT 1,
    error_message  VARCHAR(1000) NULL,
    duration_ms    INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_tool_user_repo ON tool_call_log (user_id, repo_id, created_at);

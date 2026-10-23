-- 演示数据：TaskFlow 建表脚本
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `nickname` VARCHAR(50) NULL,
    `email` VARCHAR(100) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
);

CREATE TABLE IF NOT EXISTS `task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'TODO' COMMENT 'TODO/IN_PROGRESS/DONE',
    `assignee_id` BIGINT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_task_user` (`user_id`),
    KEY `idx_task_status` (`status`)
);

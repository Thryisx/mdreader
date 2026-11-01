CREATE TABLE `user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`      VARCHAR(50)     NOT NULL                   COMMENT '登录用户名',
    `password_hash` VARCHAR(255)    NOT NULL                   COMMENT '密码（开发期明文，上线前改为哈希）',
    `email`         VARCHAR(255)    NULL                       COMMENT '邮箱，可选',
    `role`          VARCHAR(16)     NOT NULL DEFAULT 'USER'    COMMENT '角色：ADMIN / USER',
    `status`        TINYINT         NOT NULL DEFAULT 1         COMMENT '账号状态：1 启用，0 禁用',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    CONSTRAINT `chk_user_role` CHECK (`role` IN ('ADMIN', 'USER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

INSERT INTO `user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '1234', 'ADMIN', 1);

CREATE TABLE `files` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件 ID',
    `user_id`          BIGINT UNSIGNED NOT NULL                COMMENT '所属用户 ID',
    `title`            VARCHAR(255)    NOT NULL                COMMENT '文档标题',
    `original_name`    VARCHAR(255)    NULL                    COMMENT '原始文件名',
    `md_url`           VARCHAR(2048)   NOT NULL                COMMENT 'Markdown 文件 URL',
    `md_object_key`    VARCHAR(512)    NULL                    COMMENT '对象存储 key，用于删除/迁移',
    `storage_provider` VARCHAR(32)     NOT NULL                COMMENT 'ALIYUN_OSS/TENCENT_COS/S3/LOCAL',
    `img_urls`         JSON            NULL                    COMMENT '图片对象数组',
    `file_size`        BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
    `visibility`       VARCHAR(16)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC / PRIVATE',
    `status`           VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'   COMMENT 'DRAFT/PENDING/PUBLISHED/REJECTED',
    `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_files_user_updated` (`user_id`, `updated_at`),
    KEY `idx_files_feed` (`visibility`, `status`, `created_at`),
    CONSTRAINT `fk_files_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `chk_files_visibility` CHECK (`visibility` IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT `chk_files_status` CHECK (`status` IN ('DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Markdown 文件表';

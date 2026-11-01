ALTER TABLE `files`
    ADD COLUMN `category` VARCHAR(64) NULL COMMENT '文章分类，为空表示未分类' AFTER `status`,
    ADD COLUMN `summary` VARCHAR(500) NULL COMMENT '文章摘要，为空时由服务端按正文自动生成' AFTER `category`;

ALTER TABLE `files`
    ADD KEY `idx_files_public_category` (`visibility`, `status`, `node_type`, `category`, `created_at`, `id`);

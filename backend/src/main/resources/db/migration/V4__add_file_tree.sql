ALTER TABLE `files`
    ADD COLUMN `parent_id` BIGINT UNSIGNED NULL COMMENT '父节点 ID；NULL 表示位于根目录' AFTER `user_id`,
    ADD COLUMN `node_type` VARCHAR(16) NOT NULL DEFAULT 'FILE' COMMENT '节点类型：FILE / FOLDER' AFTER `parent_id`;

ALTER TABLE `files`
    ADD KEY `idx_files_user_parent` (`user_id`, `parent_id`),
    ADD KEY `idx_files_user_type` (`user_id`, `node_type`);

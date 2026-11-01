ALTER TABLE `files`
    ADD COLUMN `parent_key` BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(`parent_id`, 0)) STORED
        COMMENT '用于同级唯一约束的父节点键，根目录为 0' AFTER `parent_id`;

ALTER TABLE `files`
    ADD UNIQUE KEY `uk_files_user_parent_title` (`user_id`, `parent_key`, `title`);

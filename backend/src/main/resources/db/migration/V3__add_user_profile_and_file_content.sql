ALTER TABLE `user`
    ADD COLUMN `nickname` VARCHAR(50) NULL COMMENT '用户昵称' AFTER `username`,
    ADD COLUMN `avatar` VARCHAR(2048) NULL COMMENT '头像访问 URL' AFTER `nickname`;

ALTER TABLE `files`
    ADD COLUMN `content` LONGTEXT NULL COMMENT 'Markdown 正文，NULL 表示使用外部 md_url' AFTER `original_name`;

ALTER TABLE `user`
    ADD COLUMN `token_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Token 版本号，改密/禁用/降权时递增' AFTER `status`;

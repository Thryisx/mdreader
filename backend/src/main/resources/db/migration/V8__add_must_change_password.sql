ALTER TABLE `user`
    ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否必须修改密码：1 是，0 否' AFTER `token_version`;

UPDATE `user`
SET `must_change_password` = 1
WHERE `username` = 'admin';

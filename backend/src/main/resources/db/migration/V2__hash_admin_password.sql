-- 将 V1 种子管理员的明文密码更新为 PBKDF2-HMAC-SHA256 哈希
-- 哈希格式：pbkdf2_sha256$迭代次数$Base64盐$Base64哈希
UPDATE `user`
SET `password_hash` = 'pbkdf2_sha256$120000$YEo16CzoFowXRH49fXJHJw==$Ra/Oxls0NgHZilY/IqfhevG5F9kkDnFSw8dLs+GCEXs='
WHERE `username` = 'admin';

package com.mdreader.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希与校验工具。
 */
public final class PasswordUtil {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final String SEPARATOR = "$";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int MIN_ITERATIONS = 10_000;
    private static final int MAX_ITERATIONS = 1_000_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private PasswordUtil() {
    }

    /**
     * 生成密码哈希。
     *
     * @param rawPassword 原始密码
     * @return 形如 pbkdf2_sha256$迭代次数$盐$哈希 的字符串
     * 策略：使用 PBKDF2-HMAC-SHA256、16 字节随机盐和 600000 次迭代；旧哈希按自身迭代次数校验
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, ITERATIONS);
        return PREFIX + SEPARATOR + ITERATIONS + SEPARATOR
                + Base64.getEncoder().encodeToString(salt) + SEPARATOR
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验原始密码与存储哈希是否匹配。
     *
     * @param rawPassword 原始密码
     * @param storedHash  数据库中的密码哈希
     * @return 是否匹配；哈希格式非法时返回 false
     * 策略：解析哈希中的迭代次数和盐后重新计算，使用常量时间比较避免时序攻击；算法不可用时抛出 IllegalStateException
     */
    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.isEmpty()
                || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        String[] parts = storedHash.split("\\" + SEPARATOR, -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            if (salt.length == 0 || expectedHash.length == 0) {
                return false;
            }
            byte[] actualHash = pbkdf2(rawPassword, salt, iterations);
            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 执行 PBKDF2 派生。
     *
     * @param rawPassword 原始密码
     * @param salt        盐
     * @param iterations  迭代次数
     * @return 派生出的哈希字节
     * 策略：每次调用创建独立的 SecretKeyFactory，最后清理 PBEKeySpec 中的密码副本
     */
    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, HASH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("密码哈希算法不可用", exception);
        } finally {
            keySpec.clearPassword();
        }
    }
}

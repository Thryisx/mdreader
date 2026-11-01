package com.mdreader.utils;

import com.mdreader.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 系统统一密码策略。
 */
public final class PasswordPolicy {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=(?:.*\\d){8})(?=(?:.*[A-Za-z]){2})[A-Za-z0-9]{10}$");

    private PasswordPolicy() {
    }

    /**
     * 校验新密码是否符合统一策略。
     *
     * @param password 原始密码
     * 策略：要求总长 10 位，由 8 位数字和 2 位英文字母组成，顺序不限；不裁剪、不允许空格
     */
    public static void validate(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessException(400, "密码必须为10位，且包含8位数字和2位英文字母");
        }
    }
}

package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户注册请求参数。
 */
@Getter
@Setter
public class RegisterDTO {

    /**
     * 登录用户名，3-50 位字母、数字或下划线
     */
    private String username;

    /**
     * 登录密码，必须为 8 位数字 + 2 位英文字母，共 10 位
     */
    private String password;

    /**
     * 确认密码，必须与 password 完全一致
     */
    private String confirmPassword;

    /**
     * 图形验证码标识
     */
    private String captchaId;

    /**
     * 用户输入的图形验证码
     */
    private String captchaCode;

    /**
     * 用户昵称，可选，最多 50 个字符
     */
    private String nickname;

    /**
     * 邮箱，可选，必须全局唯一
     */
    private String email;
}

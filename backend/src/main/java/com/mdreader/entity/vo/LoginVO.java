package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录成功返回信息。
 */
@Getter
@Setter
public class LoginVO {

    /**
     * JWT Token
     */
    private String token;

    /**
     * Token 类型，固定为 Bearer
     */
    private String tokenType;

    /**
     * Token 有效期（秒）
     */
    private Long expiresIn;

    /**
     * 登录用户信息
     */
    private UserVO user;
}

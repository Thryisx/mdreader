package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求参数。
 */
@Getter
@Setter
public class LoginDTO {

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 登录密码
     */
    private String password;
}

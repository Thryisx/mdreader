package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员新增用户请求参数。
 */
@Getter
@Setter
public class CreateUserDTO {

    /**
     * 登录用户名，3-50 位字母、数字或下划线
     */
    private String username;

    /**
     * 登录密码，10 位且包含 8 位数字和 2 位英文字母
     */
    private String password;

    /**
     * 用户昵称，可选，最多 50 个字符
     */
    private String nickname;

    /**
     * 邮箱，可选，必须全局唯一
     */
    private String email;

    /**
     * 角色：ADMIN / USER，默认 USER
     */
    private String role;

    /**
     * 账号状态：1 启用，0 禁用，默认 1
     */
    private Integer status;
}

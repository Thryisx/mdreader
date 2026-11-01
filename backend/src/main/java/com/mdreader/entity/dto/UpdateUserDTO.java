package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员修改用户请求参数。
 */
@Getter
@Setter
public class UpdateUserDTO {

    /**
     * 用户昵称；null 表示不修改，空字符串表示清空
     */
    private String nickname;

    /**
     * 邮箱；null 表示不修改，空字符串表示清空
     */
    private String email;

    /**
     * 角色：ADMIN / USER；null 表示不修改
     */
    private String role;

    /**
     * 账号状态：1 启用，0 禁用；null 表示不修改
     */
    private Integer status;
}

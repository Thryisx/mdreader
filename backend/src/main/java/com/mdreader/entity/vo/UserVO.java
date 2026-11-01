package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户信息展示对象。
 */
@Getter
@Setter
public class UserVO {

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 用户昵称（可为空，为空时前端使用 username 展示）
     */
    private String nickname;

    /**
     * 头像访问 URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色：ADMIN / USER
     */
    private String role;

    /**
     * 账号状态：1 启用，0 禁用
     */
    private Integer status;

    /**
     * 是否必须修改密码
     */
    private Boolean mustChangePassword;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

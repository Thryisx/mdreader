package com.mdreader.entity.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户持久化对象，对应数据库 user 表。
 */
@Getter
@Setter
public class UserDO {

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 登录用户名（唯一）
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
     * 密码哈希（PBKDF2-HMAC-SHA256）
     */
    private String passwordHash;

    /**
     * 邮箱（可选，唯一）
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
     * Token 版本号：改密、禁用或降权时递增，用于使旧 Token 失效
     */
    private Integer tokenVersion;

    /**
     * 是否必须修改密码：1 是，0 否
     */
    private Integer mustChangePassword;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

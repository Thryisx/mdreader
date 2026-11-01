package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改当前用户资料请求参数。
 */
@Getter
@Setter
public class UpdateProfileDTO {

    /**
     * 用户昵称；null 表示不修改，空字符串表示清空
     */
    private String nickname;

    /**
     * 邮箱；null 表示不修改，空字符串表示清空
     */
    private String email;

    /**
     * 头像 URL；null 表示不修改，空字符串表示清空
     */
    private String avatar;
}

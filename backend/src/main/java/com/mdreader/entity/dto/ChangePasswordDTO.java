package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 当前用户修改密码参数。
 */
@Getter
@Setter
public class ChangePasswordDTO {

    /**
     * 当前密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String confirmPassword;
}

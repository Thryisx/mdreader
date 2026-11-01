package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员重置用户密码请求参数。
 */
@Getter
@Setter
public class ResetPasswordDTO {

    /**
     * 新密码，10 位且包含 8 位数字和 2 位英文字母
     */
    private String password;
}

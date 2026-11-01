package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员启用/禁用用户请求参数。
 */
@Getter
@Setter
public class UpdateUserStatusDTO {

    /**
     * 账号状态：1 启用，0 禁用
     */
    private Integer status;
}

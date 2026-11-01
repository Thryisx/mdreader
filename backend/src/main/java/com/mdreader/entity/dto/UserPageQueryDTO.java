package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户分页查询参数。
 */
@Getter
@Setter
public class UserPageQueryDTO extends PageQueryDTO {

    /**
     * 用户名或邮箱模糊搜索关键字
     */
    private String keyword;

    /**
     * 角色筛选：ADMIN / USER
     */
    private String role;

    /**
     * 账号状态筛选：1 启用，0 禁用
     */
    private Integer status;
}

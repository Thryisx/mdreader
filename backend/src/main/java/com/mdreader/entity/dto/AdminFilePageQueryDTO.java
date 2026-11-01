package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员文件分页查询参数。
 */
@Getter
@Setter
public class AdminFilePageQueryDTO extends PageQueryDTO {

    /**
     * 所属用户 ID 筛选
     */
    private Long userId;

    /**
     * 可见性筛选：PUBLIC / PRIVATE
     */
    private String visibility;

    /**
     * 文件状态筛选：DRAFT / PENDING / PUBLISHED / REJECTED
     */
    private String status;

    /**
     * 标题或原始文件名模糊搜索关键字
     */
    private String keyword;
}

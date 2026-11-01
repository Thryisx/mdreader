package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 通用分页查询参数。
 */
@Getter
@Setter
public class PageQueryDTO {

    /**
     * 页码，从 1 开始
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;
}

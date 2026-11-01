package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 公开文件分页查询参数。
 */
@Getter
@Setter
public class FilePageQueryDTO extends PageQueryDTO {

    /**
     * 文件标题模糊搜索关键字
     */
    private String keyword;

    /**
     * 文章分类筛选；为空时不筛选，未分类用空字符串由前端传特殊值 UNCATEGORIZED
     */
    private String category;
}

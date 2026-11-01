package com.mdreader.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 公开文章分类计数展示对象。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileCategoryVO {

    /**
     * 文章分类；未分类返回 null
     */
    private String category;

    /**
     * 该分类下公开已发布文件数量
     */
    private long count;
}

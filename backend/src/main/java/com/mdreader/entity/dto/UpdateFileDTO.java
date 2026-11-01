package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存文件请求参数。
 */
@Getter
@Setter
public class UpdateFileDTO {

    /**
     * 文件标题；null 表示不修改
     */
    private String title;

    /**
     * 原始文件名；null 表示不修改
     */
    private String originalName;

    /**
     * 文章分类；null 表示不修改，空字符串表示清空为未分类
     */
    private String category;

    /**
     * 文章摘要；null 表示不修改，空字符串表示清空后按正文自动生成
     */
    private String summary;

    /**
     * Markdown 正文；null 表示不修改
     */
    private String content;

    /**
     * 可见性：PUBLIC / PRIVATE；null 表示不修改
     */
    private String visibility;

    /**
     * 文件状态：DRAFT / PENDING / PUBLISHED / REJECTED；null 表示不修改
     */
    private String status;
}

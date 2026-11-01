package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建文件请求参数。
 */
@Getter
@Setter
public class CreateFileDTO {

    /**
     * 文件标题
     */
    private String title;

    /**
     * 原始文件名；为空时由标题生成
     */
    private String originalName;

    /**
     * 父文件夹 ID；为空时创建在根目录
     */
    private Long parentId;

    /**
     * 文章分类；为空表示未分类，最多 64 个字符
     */
    private String category;

    /**
     * 文章摘要；为空时由服务端按正文自动生成，最多 500 个字符
     */
    private String summary;

    /**
     * Markdown 正文；为空时保存为空字符�?     */
    private String content;

    /**
     * 可见性：PUBLIC / PRIVATE，默认 PRIVATE
     */
    private String visibility;

    /**
     * 文件状态：DRAFT / PENDING / PUBLISHED / REJECTED，默认 DRAFT
     */
    private String status;
}

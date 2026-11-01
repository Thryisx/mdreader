package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 当前用户文件列表项。
 */
@Getter
@Setter
public class FileListItemVO {

    /**
     * 文件 ID
     */
    private Long id;

    /**
     * 文件标题
     */
    private String title;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最晚修改时间
     */
    private LocalDateTime updatedAt;
}

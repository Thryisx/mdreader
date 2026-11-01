package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理员文件列表展示对象，仅包含元数据。
 */
@Getter
@Setter
public class AdminFileVO {

    /**
     * 文件 ID
     */
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 文件标题
     */
    private String title;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 存储提供方
     */
    private String storageProvider;

    /**
     * 封面 URL
     */
    private String coverUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 可见性：PUBLIC / PRIVATE
     */
    private String visibility;

    /**
     * 状态：DRAFT / PENDING / PUBLISHED / REJECTED
     */
    private String status;

    /**
     * 文章分类；为空表示未分类
     */
    private String category;

    /**
     * 作者昵称
     */
    private String authorNickname;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

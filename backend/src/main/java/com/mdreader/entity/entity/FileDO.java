package com.mdreader.entity.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文件持久化对象，对应数据库 files 表。
 */
@Getter
@Setter
public class FileDO {

    /**
     * 文件 ID
     */
    private Long id;

    /**
     * 所属用�?ID
     */
    private Long userId;

    /**
     * 父节点 ID；NULL 表示位于用户根目录
     */
    private Long parentId;

    /**
     * 节点类型：FILE / FOLDER
     */
    private String nodeType;

    /**
     * 文件标题（用户可见名称，也是文件树节点名称）
     */
    private String title;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * Markdown 正文；为空表示正文仍由外部 md_url 提供
     */
    private String content;

    /**
     * Markdown 文件访问 URL
     */
    private String mdUrl;

    /**
     * 对象存储中的 Markdown key，用于删除/迁移
     */
    private String mdObjectKey;

    /**
     * 存储提供方：ALIYUN_OSS / TENCENT_COS / S3 / LOCAL
     */
    private String storageProvider;

    /**
     * 图片对象数组 JSON 字符串
     */
    private String imgUrls;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 可见性：PUBLIC / PRIVATE
     */
    private String visibility;

    /**
     * 文件状态：DRAFT / PENDING / PUBLISHED / REJECTED
     */
    private String status;

    /**
     * 文章分类；为空表示未分类
     */
    private String category;

    /**
     * 文章摘要；为空时由服务端按正文自动生成
     */
    private String summary;

    /**
     * 作者昵称（联查 user 表派生，不落库；为空时前端用作者用户名兜底）
     */
    private String authorNickname;

    /**
     * 作者用户名（联查 user 表派生，不落库）
     */
    private String authorUsername;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

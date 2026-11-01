package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件详情展示对象。
 */
@Getter
@Setter
public class FileDetailVO {

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
     * Markdown 文件访问 URL
     */
    private String mdUrl;

    /**
     * 存储提供方：ALIYUN_OSS / TENCENT_COS / S3 / LOCAL
     */
    private String storageProvider;

    /**
     * 图片信息列表
     */
    private List<ImageInfoVO> images;

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
     * 作者昵称；为空时前端用作者用户名兜底
     */
    private String authorNickname;

    /**
     * 作者用户名
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

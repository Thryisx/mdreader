package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 文件关联图片信息。
 */
@Getter
@Setter
public class ImageInfoVO {

    /**
     * 图片访问 URL
     */
    private String url;

    /**
     * 对象存储中的图片 key
     */
    private String objectKey;

    /**
     * 存储提供方：ALIYUN_OSS / TENCENT_COS / S3 / LOCAL
     */
    private String provider;

    /**
     * 图片替代文本
     */
    private String alt;
}

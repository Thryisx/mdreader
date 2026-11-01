package com.mdreader.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地上传文件配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /**
     * 头像文件保存目录，相对于进程工作目录或绝对路径
     */
    private String avatarDir = "uploads/avatars";

    /**
     * 头像访问 URL 前缀，必须以 / 开头并以 / 结尾
     */
    private String avatarUrlPrefix = "/uploads/avatars/";

    /**
     * 头像文件大小上限（字节），默认 2MB
     */
    private long maxAvatarBytes = 2 * 1024 * 1024;
}

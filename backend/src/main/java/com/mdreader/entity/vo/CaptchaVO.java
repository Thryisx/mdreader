package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 图形验证码响应。
 */
@Getter
@Setter
public class CaptchaVO {

    /**
     * 验证码标识，注册时需原样回传
     */
    private String captchaId;

    /**
     * PNG 图片的 data URL，可直接用于 img 标签
     */
    private String image;

    /**
     * 有效期（秒）
     */
    private Integer expiresIn;
}

package com.mdreader.service;

import com.mdreader.entity.vo.CaptchaVO;

/**
 * 图形验证码服务。
 */
public interface CaptchaService {

    /**
     * 生成一次性图形验证码。
     *
     * @return 验证码标识、图片 data URL 和有效期
     * 策略：生成随机字符并渲染为 PNG，验证码保存在内存中，过期后不可使用
     */
    CaptchaVO generate();

    /**
     * 校验并消费验证码。
     *
     * @param captchaId   验证码标识
     * @param captchaCode 用户输入的验证码
     * 策略：无论校验成功或失败都从内存移除该验证码，防止重复使用；不区分大小写
     */
    void verifyAndConsume(String captchaId, String captchaCode);
}

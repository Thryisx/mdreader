package com.mdreader.service;

import com.mdreader.entity.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前用户头像服务。
 */
public interface AvatarService {

    /**
     * 上传并替换当前用户头像。
     *
     * @param file 头像文件
     * @return 更新后的当前用户信息
     * 策略：校验图片类型和大小，保存到本地目录，更新用户 avatar 字段，并尝试删除旧头像
     */
    UserVO uploadCurrentUserAvatar(MultipartFile file);
}

package com.mdreader.service.impl;

import com.mdreader.common.context.UserContext;
import com.mdreader.common.exception.BusinessException;
import com.mdreader.config.UploadProperties;
import com.mdreader.entity.entity.UserDO;
import com.mdreader.entity.vo.UserVO;
import com.mdreader.mapper.UserMapper;
import com.mdreader.service.AvatarService;
import com.mdreader.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 当前用户头像服务实现。
 */
@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarServiceImpl.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final UserMapper userMapper;
    private final UserService userService;
    private final UploadProperties uploadProperties;

    /**
     * 上传并替换当前用户头像。
     *
     * @param file 头像文件
     * @return 更新后的当前用户信息
     * 策略：校验图片类型和大小，使用 UUID 文件名保存到本地目录，更新用户 avatar 字段，并尝试删除旧头像
     */
    @Override
    public UserVO uploadCurrentUserAvatar(MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录，请先登录");
        }
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        validate(file);

        String extension = resolveExtension(file);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path avatarDir = resolveAvatarDir();
        Path target = avatarDir.resolve(filename).normalize();
        if (!target.startsWith(avatarDir)) {
            throw new BusinessException(400, "头像文件名不合法");
        }

        try {
            Files.createDirectories(avatarDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            log.error("Failed to save avatar for user {}", userId, exception);
            throw new BusinessException(500, "头像保存失败");
        }

        String avatarUrl = normalizeUrlPrefix(uploadProperties.getAvatarUrlPrefix()) + filename;
        userMapper.updateAvatar(userId, avatarUrl);
        deleteOldAvatar(user.getAvatar());
        return userService.getById(userId);
    }

    /**
     * 校验头像文件类型和大小。
     *
     * @param file 头像文件
     * 策略：拒绝空文件、超过配置上限的文件和非 image/* 文件
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的头像");
        }
        if (file.getSize() > uploadProperties.getMaxAvatarBytes()) {
            throw new BusinessException(400, "头像大小不能超过2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(400, "头像必须是图片文件");
        }
        if (!ALLOWED_EXTENSIONS.contains(resolveExtension(file))) {
            throw new BusinessException(400, "仅支持 jpg、jpeg、png、gif、webp 格式");
        }
    }

    /**
     * 解析头像扩展名。
     *
     * @param file 头像文件
     * @return 小写扩展名
     * 策略：优先使用原始文件名后缀；无法识别时根据 Content-Type 推断
     */
    private String resolveExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
                if (ALLOWED_EXTENSIONS.contains(extension)) {
                    return "jpeg".equals(extension) ? "jpg" : extension;
                }
            }
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            return "";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    /**
     * 解析头像保存目录。
     *
     * @return 规范化后的绝对目录
     * 策略：将配置路径转换为绝对路径并规范化，防止相对路径产生歧义
     */
    private Path resolveAvatarDir() {
        return Paths.get(uploadProperties.getAvatarDir()).toAbsolutePath().normalize();
    }

    /**
     * 规范化头像 URL 前缀。
     *
     * @param prefix 配置的前缀
     * @return 以 / 开头并以 / 结尾的前缀
     * 策略：空值使用默认前缀，自动补齐首尾斜杠
     */
    private String normalizeUrlPrefix(String prefix) {
        String value = prefix == null || prefix.isBlank() ? "/uploads/avatars/" : prefix.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    /**
     * 删除旧头像文件。
     *
     * @param avatarUrl 旧头像 URL
     * 策略：只删除本地上传目录下的文件；外部 URL 和非当前前缀的地址不处理
     */
    private void deleteOldAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        String prefix = normalizeUrlPrefix(uploadProperties.getAvatarUrlPrefix());
        if (!avatarUrl.startsWith(prefix)) {
            return;
        }
        String filename = avatarUrl.substring(prefix.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            return;
        }
        Path avatarDir = resolveAvatarDir();
        Path oldFile = avatarDir.resolve(filename).normalize();
        if (!oldFile.startsWith(avatarDir)) {
            return;
        }
        try {
            Files.deleteIfExists(oldFile);
        } catch (IOException exception) {
            log.warn("Failed to delete old avatar file {}", oldFile, exception);
        }
    }
}

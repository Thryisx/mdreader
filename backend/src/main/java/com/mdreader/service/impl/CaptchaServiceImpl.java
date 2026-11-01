package com.mdreader.service.impl;

import com.mdreader.common.exception.BusinessException;
import com.mdreader.entity.vo.CaptchaVO;
import com.mdreader.service.CaptchaService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存存储的图形验证码服务实现。
 */
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final int CODE_LENGTH = 4;
    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 40;
    private static final int EXPIRE_SECONDS = 120;
    private static final long EXPIRE_MILLIS = EXPIRE_SECONDS * 1000L;
    private static final char[] CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    /**
     * 生成一次性图形验证码。
     *
     * @return 验证码标识、图片 data URL 和有效期
     * 策略：每次生成新的随机标识和 4 位字符，图片以 PNG Base64 返回；同时清理已过期验证码
     */
    @Override
    public CaptchaVO generate() {
        removeExpired();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        byte[] imageBytes = renderImage(code);
        captchaStore.put(captchaId, new CaptchaEntry(code, System.currentTimeMillis() + EXPIRE_MILLIS));

        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaId(captchaId);
        captchaVO.setImage("data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes));
        captchaVO.setExpiresIn(EXPIRE_SECONDS);
        return captchaVO;
    }

    /**
     * 校验并消费验证码。
     *
     * @param captchaId   验证码标识
     * @param captchaCode 用户输入的验证码
     * 策略：先原子移除验证码，再检查是否存在、是否过期、是否匹配；任何校验失败均返回 400
     */
    @Override
    public void verifyAndConsume(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()
                || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(400, "请输入验证码");
        }

        CaptchaEntry entry = captchaStore.remove(captchaId.trim());
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            throw new BusinessException(400, "验证码已过期，请刷新后重试");
        }
        if (!entry.code().equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(400, "验证码错误");
        }
    }

    /**
     * 生成随机验证码字符。
     *
     * @return 4 位验证码
     * 策略：从去除易混淆字符的字符集中逐位随机选择
     */
    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CAPTCHA_CHARS[random.nextInt(CAPTCHA_CHARS.length)]);
        }
        return code.toString();
    }

    /**
     * 将验证码渲染为 PNG 图片。
     *
     * @param code 验证码
     * @return PNG 字节数组
     * 策略：绘制浅色背景、干扰线、噪点和带轻微旋转的字符，使用无衬线字体，失败时抛出运行时异常
     */
    private byte[] renderImage(String code) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(247, 249, 252));
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            graphics.setColor(new Color(214, 222, 234));
            graphics.drawRect(0, 0, IMAGE_WIDTH - 1, IMAGE_HEIGHT - 1);

            for (int i = 0; i < 6; i++) {
                graphics.setColor(randomColor(150, 220));
                graphics.drawLine(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT),
                        random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT));
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                AffineTransform original = graphics.getTransform();
                double angle = (random.nextDouble() - 0.5) * 0.28;
                graphics.rotate(angle, 18 + i * 25, 28);
                graphics.setColor(randomColor(40, 120));
                graphics.drawString(String.valueOf(code.charAt(i)), 10 + i * 25 + random.nextInt(4),
                        28 + random.nextInt(4));
                graphics.setTransform(original);
            }

            for (int i = 0; i < 35; i++) {
                graphics.setColor(randomColor(120, 220));
                graphics.fillOval(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT), 2, 2);
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("验证码图片生成失败", exception);
        }
    }

    /**
     * 生成指定区间内的随机颜色。
     *
     * @param min 最小 RGB 分量
     * @param max 最大 RGB 分量
     * @return 随机颜色
     * 策略：三个分量分别随机，用于干扰线和噪点
     */
    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }

    /**
     * 清理已过期验证码。
     * 策略：每次生成新验证码时遍历并移除过期项，避免内存无限增长
     */
    private void removeExpired() {
        long now = System.currentTimeMillis();
        captchaStore.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    /**
     * 内存中的验证码记录。
     *
     * @param code      验证码字符
     * @param expiresAt 过期时间戳（毫秒）
     */
    private record CaptchaEntry(String code, long expiresAt) {
    }
}

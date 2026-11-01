package com.mdreader.service.impl;

import com.mdreader.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制服务。
 */
@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 检查当前用户名和来源 IP 是否已被临时锁定。
     *
     * @param username 登录用户名
     * 策略：15 分钟内连续失败 5 次后锁定 15 分钟，锁定期间返回 429
     */
    public void checkAllowed(String username) {
        removeExpired();
        Attempt attempt = attempts.get(buildKey(username));
        if (attempt != null && attempt.lockedUntil() > System.currentTimeMillis()) {
            throw new BusinessException(429, "登录失败次数过多，请15分钟后再试");
        }
    }

    /**
     * 记录一次登录失败。
     *
     * @param username 登录用户名
     * 策略：按“来源 IP + 用户名”累计，窗口过期后重新计数；达到阈值时写入锁定截止时间
     */
    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        String key = buildKey(username);
        attempts.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStart() > WINDOW_MILLIS) {
                return new Attempt(1, now, 0L);
            }
            int failures = current.failures() + 1;
            long lockedUntil = failures >= MAX_FAILURES ? now + LOCK_MILLIS : 0L;
            return new Attempt(failures, current.windowStart(), lockedUntil);
        });
        removeExpired();
    }

    /**
     * 清除指定用户名和来源 IP 的失败记录。
     *
     * @param username 登录用户名
     * 策略：登录成功后清除，避免正常用户持续受到历史失败次数影响
     */
    public void clear(String username) {
        attempts.remove(buildKey(username));
    }

    /**
     * 清理过期记录，避免内存无限增长。
     *
     * 策略：窗口和锁定均过期后移除；超过容量上限时直接清空，保证服务可用性
     */
    private void removeExpired() {
        if (attempts.size() > MAX_ENTRIES) {
            attempts.clear();
            return;
        }
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            return attempt.lockedUntil() < now && now - attempt.windowStart() > WINDOW_MILLIS;
        });
    }

    /**
     * 构造限流键。
     *
     * @param username 登录用户名
     * @return 来源 IP 与规范化用户名组成的键
     * 策略：用户名忽略大小写，未取得请求上下文时使用 unknown，便于测试和异步调用
     */
    private String buildKey(String username) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return clientIp() + "|" + normalizedUsername;
    }

    /**
     * 获取客户端来源 IP。
     *
     * @return 来源 IP；无请求上下文时返回 unknown
     * 策略：使用容器解析后的 remoteAddr，不直接信任客户端可伪造的转发头
     */
    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    private record Attempt(int failures, long windowStart, long lockedUntil) {
    }
}

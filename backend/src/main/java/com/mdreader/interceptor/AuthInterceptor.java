package com.mdreader.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdreader.common.Result;
import com.mdreader.common.annotation.AllowDuringPasswordChange;
import com.mdreader.common.annotation.OptionalAuth;
import com.mdreader.common.annotation.RequireAdmin;
import com.mdreader.common.constant.SystemConstants;
import com.mdreader.common.context.UserContext;
import com.mdreader.entity.entity.UserDO;
import com.mdreader.mapper.UserMapper;
import com.mdreader.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 鉴权拦截器。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int ENABLED_STATUS = 1;

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    /**
     * 请求进入 Controller 前校验 Token 和角色。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return 是否放行
     * 策略：解析 Bearer Token，并回查数据库校验用户状态、角色和 Token 版本；管理员接口通过 @RequireAdmin 判断
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        boolean optionalAuth = hasAnnotation(handler, OptionalAuth.class);
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || authorization.isBlank()) {
            if (optionalAuth) {
                return true;
            }
            return writeError(response, HttpStatus.UNAUTHORIZED.value(), "未登录，请先登录");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            return writeError(response, HttpStatus.UNAUTHORIZED.value(), "Token 格式错误");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return writeError(response, HttpStatus.UNAUTHORIZED.value(), "Token 格式错误");
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = jwtUtil.getUserId(claims);
            String tokenRole = jwtUtil.getRole(claims);
            Integer tokenVersion = jwtUtil.getTokenVersion(claims);
            if (userId == null || tokenRole == null || tokenRole.isBlank()) {
                return writeError(response, HttpStatus.UNAUTHORIZED.value(), "Token 无效，请重新登录");
            }

            UserDO user = userMapper.selectById(userId);
            if (user == null) {
                return writeError(response, HttpStatus.UNAUTHORIZED.value(), "用户不存在或登录状态已失效");
            }
            if (!Integer.valueOf(ENABLED_STATUS).equals(user.getStatus())) {
                return writeError(response, HttpStatus.UNAUTHORIZED.value(), "账号已被禁用，请重新登录");
            }
            if (!tokenRole.equals(user.getRole())
                    || !tokenVersion.equals(user.getTokenVersion() == null ? 0 : user.getTokenVersion())) {
                return writeError(response, HttpStatus.UNAUTHORIZED.value(), "登录状态已失效，请重新登录");
            }
            if (Integer.valueOf(1).equals(user.getMustChangePassword())
                    && !hasAnnotation(handler, AllowDuringPasswordChange.class)) {
                return writeError(response, HttpStatus.FORBIDDEN.value(), "请先修改初始密码");
            }
            if (requiresAdmin(handler) && !SystemConstants.ROLE_ADMIN.equals(user.getRole())) {
                return writeError(response, HttpStatus.FORBIDDEN.value(), "无权限访问");
            }
            UserContext.set(userId, user.getRole());
            return true;
        } catch (ExpiredJwtException exception) {
            return writeError(response, HttpStatus.UNAUTHORIZED.value(), "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException exception) {
            return writeError(response, HttpStatus.UNAUTHORIZED.value(), "Token 无效，请重新登录");
        }
    }

    /**
     * 请求结束后清理用户上下文。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       请求异常
     * 策略：无论请求成功或失败都调用 UserContext.clear()，防止 ThreadLocal 泄漏
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    /**
     * 判断处理器是否要求管理员角色。
     *
     * @param handler Spring MVC 处理器
     * @return 是否要求 ADMIN
     * 策略：读取方法或控制器类上的 @RequireAdmin，不依赖原始 URI 字符串
     */
    private boolean requiresAdmin(Object handler) {
        return hasAnnotation(handler, RequireAdmin.class);
    }

    /**
     * 判断处理器是否带有指定注解。
     *
     * @param handler          Spring MVC 处理器
     * @param annotationType   注解类型
     * @return 方法或控制器类是否声明了该注解
     * 策略：同时检查 HandlerMethod 的方法和所属控制器类
     */
    private boolean hasAnnotation(Object handler, Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.hasMethodAnnotation(annotationType)
                    || handlerMethod.getBeanType().isAnnotationPresent(annotationType);
        }
        return false;
    }

    /**
     * 输出统一的鉴权错误响应。
     *
     * @param response HTTP 响应
     * @param code     HTTP 状态码 / 业务状态码
     * @param message  提示信息
     * @return 固定返回 false，表示拦截请求
     * 策略：直接写入 JSON，避免拦截器异常绕过 ControllerAdvice 导致响应格式不一致
     */
    private boolean writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
        return false;
    }
}

package com.mdreader.config;

import com.mdreader.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CorsProperties corsProperties;
    private final UploadProperties uploadProperties;

    /**
     * 注册 JWT 鉴权拦截器。
     *
     * @param registry 拦截器注册表
     * 策略：拦截 /api/**，放行登录、注册和图形验证码接口；其余接口统一要求 Token
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/captcha");
    }

    /**
     * 配置前后端分离开发所需的跨域规则。
     *
     * @param registry CORS 注册表
     * 策略：仅允许配置中的前端来源访问 /api/**，支持 Bearer Token 所需的 Authorization 请求头；不启用 Cookie 凭证
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsProperties.getAllowedOrigins() == null) {
            return;
        }
        String[] allowedOrigins = corsProperties.getAllowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .maxAge(3600);
    }

    /**
     * 配置本地上传文件的静态访问路径。
     *
     * @param registry 资源处理器注册表
     * 策略：将配置的头像 URL 前缀映射到本地头像目录，仅用于开发/单机部署
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = uploadProperties.getAvatarUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "/uploads/avatars/";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        Path avatarDir = Paths.get(uploadProperties.getAvatarDir()).toAbsolutePath().normalize();
        String location = avatarDir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(prefix + "**").addResourceLocations(location);
    }
}

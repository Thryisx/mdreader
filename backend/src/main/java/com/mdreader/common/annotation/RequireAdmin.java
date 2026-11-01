package com.mdreader.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记仅管理员可访问的控制器或接口。
 *
 * <p>由 {@code AuthInterceptor} 基于 Spring MVC 的 HandlerMethod 读取，
 * 不依赖请求 URI 字符串匹配，避免路径编码或尾斜杠造成权限判断偏差。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}

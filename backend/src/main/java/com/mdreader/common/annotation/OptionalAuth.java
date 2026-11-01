package com.mdreader.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许匿名访问、但携带 Token 时仍需校验的接口。
 *
 * <p>用于公开文件列表、详情和正文：未携带 Token 时按匿名访问处理，
 * 携带 Token 时由 {@code AuthInterceptor} 正常校验并写入当前用户上下文。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalAuth {
}

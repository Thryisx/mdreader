package com.mdreader.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记用户被要求修改密码时仍可访问的接口。
 *
 * <p>用于当前用户信息查询和修改密码接口，保证用户能在受限状态下完成改密。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowDuringPasswordChange {
}

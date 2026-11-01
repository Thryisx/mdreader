package com.mdreader.common.context;

/**
 * 当前请求用户上下文。
 *
 * <p>由鉴权拦截器在请求开始时写入，在请求结束时清理。</p>
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前请求用户信息。
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * 策略：使用 ThreadLocal 保存请求级用户数据，供同线程的 Service 读取
     */
    public static void set(Long userId, String role) {
        HOLDER.set(new CurrentUser(userId, role));
    }

    /**
     * 获取当前请求用户 ID。
     *
     * @return 用户 ID；未登录时返回 null
     */
    public static Long getUserId() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.userId();
    }

    /**
     * 获取当前请求用户角色。
     *
     * @return 角色；未登录时返回 null
     */
    public static String getRole() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.role();
    }

    /**
     * 判断当前用户是否为管理员。
     *
     * @return 是否管理员
     */
    public static boolean isAdmin() {
        return "ADMIN".equals(getRole());
    }

    /**
     * 清理当前请求用户上下文。
     *
     * 策略：调用 ThreadLocal.remove()，防止线程池复用导致用户信息串用或内存泄漏
     */
    public static void clear() {
        HOLDER.remove();
    }

    private record CurrentUser(Long userId, String role) {
    }
}

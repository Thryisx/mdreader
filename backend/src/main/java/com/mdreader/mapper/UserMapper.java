package com.mdreader.mapper;

import com.mdreader.entity.dto.UserPageQueryDTO;
import com.mdreader.entity.entity.UserDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问接口。
 */
public interface UserMapper {

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户数据；不存在时返回 null
     * 策略：按主键单表查询，包含 role、status 和 token_version，不查询 password_hash
     */
    UserDO selectById(@Param("id") Long id);

    /**
     * 根据用户名查询登录用户。
     *
     * @param username 登录用户名
     * @return 用户数据（包含 password_hash）；不存在时返回 null
     * 策略：按 username 唯一索引单表查询，仅用于登录校验
     */
    UserDO selectByUsername(@Param("username") String username);

    /**
     * 根据 ID 查询包含密码哈希的用户。
     *
     * @param id 用户 ID
     * @return 用户数据（包含 password_hash）；不存在时返回 null
     * 策略：仅用于当前用户修改密码时校验旧密码，禁止用于接口出参
     */
    UserDO selectByIdWithPassword(@Param("id") Long id);

    /**
     * 统计符合筛选条件的用户数量。
     *
     * @param query 用户名/邮箱关键字、角色、状态筛选条件
     * @return 用户总数
     * 策略：动态 SQL 拼接可选筛选条件，执行 COUNT(*)
     */
    long countPage(@Param("query") UserPageQueryDTO query);

    /**
     * 分页查询用户。
     *
     * @param query  用户名/邮箱关键字、角色、状态筛选条件
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 当前页用户列表；无数据时返回空列表
     * 策略：动态 SQL 拼接筛选条件，按创建时间倒序，使用 LIMIT offset, limit 分页
     */
    List<UserDO> selectPage(@Param("query") UserPageQueryDTO query,
                            @Param("offset") long offset,
                            @Param("limit") long limit);

    /**
     * 统计指定邮箱的用户数量。
     *
     * @param email 邮箱
     * @return 用户数量
     * 策略：按 email 唯一索引单表统计，用于注册和新增用户时校验重复
     */
    long countByEmail(@Param("email") String email);

    /**
     * 统计指定邮箱且排除指定用户后的数量。
     *
     * @param email 邮箱
     * @param id    需要排除的用户 ID
     * @return 用户数量
     * 策略：按 email 统计并排除当前用户，用于修改资料和后台编辑用户时校验重复
     */
    long countByEmailExcludingId(@Param("email") String email, @Param("id") Long id);

    /**
     * 统计启用中的管理员数量。
     *
     * @return 启用中的管理员总数
     * 策略：按 role=ADMIN 且 status=1 统计，用于保护最后一个管理员
     */
    long countEnabledAdmins();

    /**
     * 新增用户。
     *
     * @param user 用户数据
     * @return 影响行数
     * 策略：插入用户名、密码哈希、昵称、邮箱、角色和状态，并回填自增主键
     */
    int insert(UserDO user);

    /**
     * 更新当前用户资料。
     *
     * @param id       用户 ID
     * @param nickname 昵称；null 表示不修改，空字符串表示清空
     * @param email    邮箱；null 表示不修改，空字符串表示清空
     * @param avatar   头像 URL；null 表示不修改，空字符串表示清空
     * @return 影响行数
     * 策略：动态更新非 null 字段，同时刷新 updated_at
     */
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("email") String email,
                      @Param("avatar") String avatar);

    /**
     * 更新用户头像。
     *
     * @param id     用户 ID
     * @param avatar 头像 URL
     * @return 影响行数
     * 策略：只更新 avatar 和 updated_at
     */
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    /**
     * 后台更新用户资料。
     *
     * @param user 用户数据，至少包含 id
     * @return 影响行数
     * 策略：动态更新 nickname、email、role、status，同时刷新 updated_at
     */
    int updateAdmin(UserDO user);

    /**
     * 更新用户启用状态。
     *
     * @param id     用户 ID
     * @param status 账号状态：1 启用，0 禁用
     * @return 影响行数
     * 策略：只更新 status 和 updated_at
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新用户密码哈希。
     *
     * @param id           用户 ID
     * @param passwordHash PBKDF2 密码哈希
     * @return 影响行数
     * 策略：更新 password_hash，清除 must_change_password，同时递增 token_version 使旧 Token 失效
     */
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 管理员重置用户密码。
     *
     * @param id           用户 ID
     * @param passwordHash PBKDF2 密码哈希
     * @return 影响行数
     * 策略：更新 password_hash，设置 must_change_password=1，同时递增 token_version 使旧 Token 失效
     */
    int resetPassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 递增用户 Token 版本号。
     *
     * @param id 用户 ID
     * @return 影响行数
     * 策略：用于角色或状态变化后立即使旧 Token 失效
     */
    int incrementTokenVersion(@Param("id") Long id);

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     * @return 影响行数
     * 策略：按主键删除；存在外键引用时由数据库抛出约束异常
     */
    int deleteById(@Param("id") Long id);
}

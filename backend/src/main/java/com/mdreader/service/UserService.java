package com.mdreader.service;

import com.mdreader.common.PageResult;
import com.mdreader.entity.dto.CreateUserDTO;
import com.mdreader.entity.dto.UpdateUserDTO;
import com.mdreader.entity.dto.UserPageQueryDTO;
import com.mdreader.entity.vo.UserVO;

/**
 * 用户查询服务。
 */
public interface UserService {

    /**
     * 根据 ID 查询用户信息。
     *
     * @param id 用户 ID
     * @return 用户信息
     * 策略：校验 ID 后调用 mapper 单表查询，不存在时抛出 404 业务异常
     */
    UserVO getById(Long id);

    /**
     * 分页查询用户。
     *
     * @param query 分页及筛选条件
     * @return 用户分页结果
     * 策略：规范化分页参数，先统计总数再查询当前页，最后转换为 VO
     */
    PageResult<UserVO> page(UserPageQueryDTO query);

    /**
     * 管理员新增用户。
     *
     * @param createUserDTO 新增参数
     * @return 新增后的用户信息
     * 策略：校验用户名、密码、邮箱、昵称、角色和状态，检查唯一性，使用 PBKDF2 保存密码
     */
    UserVO create(CreateUserDTO createUserDTO);

    /**
     * 管理员修改用户资料。
     *
     * @param id            用户 ID
     * @param updateUserDTO 修改参数
     * @return 修改后的用户信息
     * 策略：校验目标用户存在，动态更新昵称、邮箱、角色和状态；禁止管理员禁用或降级自己
     */
    UserVO update(Long id, UpdateUserDTO updateUserDTO);

    /**
     * 管理员启用/禁用用户。
     *
     * @param id     用户 ID
     * @param status 账号状态：1 启用，0 禁用
     * @return 修改后的用户信息
     * 策略：校验状态值，禁止管理员禁用自己
     */
    UserVO updateStatus(Long id, Integer status);

    /**
     * 管理员重置用户密码。
     *
     * @param id          用户 ID
     * @param newPassword 新密码
     * 策略：校验统一密码策略后使用 PBKDF2 哈希更新，并递增 Token 版本使旧 Token 失效
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 管理员删除用户。
     *
     * @param id 用户 ID
     * 策略：禁止删除当前登录管理员；目标用户存在文件时由外键约束阻止删除
     */
    void delete(Long id);
}

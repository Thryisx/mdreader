package com.mdreader.service;

import com.mdreader.entity.dto.ChangePasswordDTO;
import com.mdreader.entity.dto.LoginDTO;
import com.mdreader.entity.dto.RegisterDTO;
import com.mdreader.entity.dto.UpdateProfileDTO;
import com.mdreader.entity.vo.LoginVO;
import com.mdreader.entity.vo.UserVO;

/**
 * 登录与当前用户服务。
 */
public interface AuthService {

    /**
     * 用户登录并签发 JWT。
     *
     * @param loginDTO 登录参数
     * @return Token 及登录用户信息
     * 策略：校验用户名密码和账号状态，成功后生成包含用户 ID、角色和用户名的 Token
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 注册普通用户并直接签发 JWT。
     *
     * @param registerDTO 注册参数
     * @return Token 及新用户信息
     * 策略：校验用户名、密码、邮箱和昵称，检查唯一性，使用 PBKDF2 保存密码；默认角色 USER、状态启用
     */
    LoginVO register(RegisterDTO registerDTO);

    /**
     * 修改当前登录用户资料。
     *
     * @param updateProfileDTO 资料修改参数
     * @return 修改后的当前用户信息
     * 策略：从 UserContext 获取用户 ID，校验昵称、邮箱和头像长度，检查邮箱唯一性后动态更新
     */
    UserVO updateCurrentUser(UpdateProfileDTO updateProfileDTO);

    /**
     * 修改当前登录用户密码。
     *
     * @param changePasswordDTO 旧密码、新密码和确认密码
     * 策略：从 UserContext 获取用户 ID，校验旧密码、新密码策略和确认密码后更新哈希，并递增 Token 版本使旧 Token 失效
     */
    void changePassword(ChangePasswordDTO changePasswordDTO);

    /**
     * 查询当前登录用户信息。
     *
     * @return 当前用户信息
     * 策略：从 UserContext 获取用户 ID，再调用用户服务查询，避免信任客户端传入的用户 ID
     */
    UserVO currentUser();
}

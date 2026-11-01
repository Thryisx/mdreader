package com.mdreader.controller;

import com.mdreader.common.Result;
import com.mdreader.common.annotation.AllowDuringPasswordChange;
import com.mdreader.entity.dto.ChangePasswordDTO;
import com.mdreader.entity.dto.LoginDTO;
import com.mdreader.entity.dto.RegisterDTO;
import com.mdreader.entity.dto.UpdateProfileDTO;
import com.mdreader.entity.vo.CaptchaVO;
import com.mdreader.entity.vo.LoginVO;
import com.mdreader.entity.vo.UserVO;
import com.mdreader.service.AuthService;
import com.mdreader.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与当前用户接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /**
     * 获取注册用图形验证码。
     *
     * @return 验证码标识、图片 data URL 和有效期
     */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * 用户登录并签发 JWT。
     *
     * @param loginDTO 登录参数
     * @return 统一响应结构中的 Token 和用户信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    /**
     * 注册普通用户并直接签发 JWT。
     *
     * @param registerDTO 注册参数
     * @return 统一响应结构中的 Token 和用户信息
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody RegisterDTO registerDTO) {
        return Result.success(authService.register(registerDTO));
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 统一响应结构中的当前用户信息
     */
    @GetMapping("/me")
    @AllowDuringPasswordChange
    public Result<UserVO> me() {
        return Result.success(authService.currentUser());
    }

    /**
     * 修改当前登录用户资料。
     *
     * @param updateProfileDTO 资料修改参数
     * @return 统一响应结构中的修改后用户信息
     */
    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestBody UpdateProfileDTO updateProfileDTO) {
        return Result.success(authService.updateCurrentUser(updateProfileDTO));
    }

    /**
     * 修改当前登录用户密码。
     *
     * @param changePasswordDTO 旧密码、新密码和确认密码
     * @return 统一成功响应；修改后旧 Token 立即失效
     */
    @PutMapping("/me/password")
    @AllowDuringPasswordChange
    public Result<Void> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO) {
        authService.changePassword(changePasswordDTO);
        return Result.success(null);
    }
}

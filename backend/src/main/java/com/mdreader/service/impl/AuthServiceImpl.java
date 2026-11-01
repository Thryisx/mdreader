package com.mdreader.service.impl;

import com.mdreader.common.constant.SystemConstants;
import com.mdreader.common.context.UserContext;
import com.mdreader.common.exception.BusinessException;
import com.mdreader.entity.dto.ChangePasswordDTO;
import com.mdreader.entity.dto.LoginDTO;
import com.mdreader.entity.dto.RegisterDTO;
import com.mdreader.entity.dto.UpdateProfileDTO;
import com.mdreader.entity.entity.UserDO;
import com.mdreader.entity.vo.LoginVO;
import com.mdreader.entity.vo.UserVO;
import com.mdreader.mapper.UserMapper;
import com.mdreader.service.AuthService;
import com.mdreader.service.CaptchaService;
import com.mdreader.service.UserService;
import com.mdreader.utils.JwtUtil;
import com.mdreader.utils.PasswordPolicy;
import com.mdreader.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 登录、注册与当前用户服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final int ENABLED_STATUS = 1;
    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_AVATAR_LENGTH = 2048;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;

    /**
     * 用户登录并签发 JWT。
     *
     * @param loginDTO 登录参数
     * @return Token 及登录用户信息
     * 策略：使用 PBKDF2 哈希校验密码，再检查账号状态；成功后生成包含用户 ID、角色和用户名的 Token
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        if (loginDTO == null
                || loginDTO.getUsername() == null || loginDTO.getUsername().isBlank()
                || loginDTO.getPassword() == null || loginDTO.getPassword().isBlank()) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }

        String username = loginDTO.getUsername().trim();
        loginAttemptService.checkAllowed(username);
        UserDO user = userMapper.selectByUsername(username);
        if (user == null || !PasswordUtil.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(username);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(ENABLED_STATUS).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        loginAttemptService.clear(username);
        return buildLoginVO(user);
    }

    /**
     * 注册普通用户并直接签发 JWT。
     *
     * @param registerDTO 注册参数
     * @return Token 及新用户信息
     * 策略：校验用户名、密码、确认密码、验证码、邮箱和昵称，检查用户名/邮箱唯一性，使用 PBKDF2 保存密码；默认角色 USER、状态启用
     */
    @Override
    public LoginVO register(RegisterDTO registerDTO) {
        if (registerDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String username = trimToNull(registerDTO.getUsername());
        String password = registerDTO.getPassword();
        String confirmPassword = registerDTO.getConfirmPassword();
        String captchaId = trimToNull(registerDTO.getCaptchaId());
        String captchaCode = trimToNull(registerDTO.getCaptchaCode());
        String nickname = trimToNull(registerDTO.getNickname());
        String email = trimToNull(registerDTO.getEmail());

        validateUsername(username);
        validatePassword(password);
        validateConfirmPassword(password, confirmPassword);
        validateNickname(nickname);
        validateEmail(email);
        captchaService.verifyAndConsume(captchaId, captchaCode);

        if (userMapper.selectByUsername(username) != null) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (email != null && userMapper.countByEmail(email) > 0) {
            throw new BusinessException(409, "邮箱已被使用");
        }

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole(SystemConstants.ROLE_USER);
        user.setStatus(ENABLED_STATUS);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "用户名或邮箱已存在");
        }

        UserDO savedUser = userMapper.selectById(user.getId());
        return buildLoginVO(savedUser);
    }

    /**
     * 修改当前登录用户资料。
     *
     * @param updateProfileDTO 资料修改参数
     * @return 修改后的当前用户信息
     * 策略：从 UserContext 获取用户 ID，校验昵称、邮箱和头像长度，检查邮箱唯一性后动态更新
     */
    @Override
    public UserVO updateCurrentUser(UpdateProfileDTO updateProfileDTO) {
        Long userId = requireCurrentUserId();
        if (updateProfileDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }

        String nickname = updateProfileDTO.getNickname();
        String email = updateProfileDTO.getEmail();
        String avatar = updateProfileDTO.getAvatar();
        if (nickname == null && email == null && avatar == null) {
            throw new BusinessException(400, "至少提供一个需要修改的字段");
        }

        if (nickname != null) {
            nickname = nickname.trim();
            validateNickname(nickname);
        }
        if (email != null) {
            email = email.trim();
            validateEmail(email);
            if (!email.isEmpty() && userMapper.countByEmailExcludingId(email, userId) > 0) {
                throw new BusinessException(409, "邮箱已被使用");
            }
        }
        if (avatar != null) {
            avatar = avatar.trim();
            if (avatar.length() > MAX_AVATAR_LENGTH) {
                throw new BusinessException(400, "头像 URL 不能超过 2048 个字符");
            }
        }

        try {
            userMapper.updateProfile(userId, nickname, email, avatar);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "邮箱已被使用");
        }
        return userService.getById(userId);
    }

    /**
     * 修改当前登录用户密码。
     *
     * @param changePasswordDTO 旧密码、新密码和确认密码
     * 策略：校验旧密码、新密码策略和确认密码，更新 PBKDF2 哈希并递增 Token 版本，旧 Token 随即失效
     */
    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        Long userId = requireCurrentUserId();
        if (changePasswordDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String oldPassword = changePasswordDTO.getOldPassword();
        String newPassword = changePasswordDTO.getNewPassword();
        String confirmPassword = changePasswordDTO.getConfirmPassword();
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BusinessException(400, "请输入当前密码");
        }

        UserDO user = userMapper.selectByIdWithPassword(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "当前密码不正确");
        }
        PasswordPolicy.validate(newPassword);
        validateConfirmPassword(newPassword, confirmPassword);
        if (PasswordUtil.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "新密码不能与当前密码相同");
        }

        userMapper.updatePassword(userId, PasswordUtil.hash(newPassword));
    }

    /**
     * 查询当前登录用户信息。
     *
     * @return 当前用户信息
     * 策略：从 UserContext 读取用户 ID，再查询数据库，避免信任客户端传入的用户 ID
     */
    @Override
    public UserVO currentUser() {
        Long userId = requireCurrentUserId();
        return userService.getById(userId);
    }

    /**
     * 构造登录响应并签发 Token。
     *
     * @param user 用户持久化对象
     * @return Token 及用户信息
     * 策略：JWT 只放用户 ID、用户名和角色，用户资料单独转换，避免携带密码哈希
     */
    private LoginVO buildLoginVO(UserDO user) {
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType(TOKEN_TYPE);
        loginVO.setExpiresIn(jwtUtil.getExpirationSeconds());
        loginVO.setUser(toUserVO(user));
        return loginVO;
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     * 策略：从 UserContext 读取，缺失时抛出 401
     */
    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录，请先登录");
        }
        return userId;
    }

    /**
     * 校验注册用户名格式。
     *
     * @param username 用户名
     * 策略：去除首尾空白后要求 3-50 位字母、数字或下划线
     */
    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(400, "用户名必须为3-50位字母、数字或下划线");
        }
    }

    /**
     * 校验注册密码格式。
     *
     * @param password 原始密码
     * 策略：要求总长 10 位，由 8 位数字和 2 位英文字母组成，不限制字母与数字的先后顺序
     */
    private void validatePassword(String password) {
        PasswordPolicy.validate(password);
    }

    /**
     * 校验两次输入的密码是否一致。
     *
     * @param password        首次输入的密码
     * @param confirmPassword 确认密码
     * 策略：要求确认密码非空且与首次输入完全一致，避免只依赖前端校验
     */
    private void validateConfirmPassword(String password, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            throw new BusinessException(400, "请再次输入密码");
        }
        if (!confirmPassword.equals(password)) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }
    }

    /**
     * 校验昵称长度。
     *
     * @param nickname 昵称
     * 策略：允许为空；非空时最多 50 个字符
     */
    private void validateNickname(String nickname) {
        if (nickname != null && nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(400, "昵称不能超过50个字符");
        }
    }

    /**
     * 校验邮箱格式。
     *
     * @param email 邮箱
     * 策略：允许为空；非空时使用基础邮箱正则校验，避免明显非法输入
     */
    private void validateEmail(String email) {
        if (email != null && !email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
    }

    /**
     * 去除首尾空白并将空字符串转换为 null。
     *
     * @param value 原始字符串
     * @return 处理后的字符串
     * 策略：避免把空白用户名、空白邮箱写入数据库
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 将登录用户持久化对象转换为展示对象。
     *
     * @param user 用户持久化对象
     * @return 用户展示对象
     * 策略：逐字段复制，主动排除 password_hash
     */
    private UserVO toUserVO(UserDO user) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(user.getAvatar());
        userVO.setEmail(user.getEmail());
        userVO.setRole(user.getRole());
        userVO.setStatus(user.getStatus());
        userVO.setMustChangePassword(Integer.valueOf(1).equals(user.getMustChangePassword()));
        userVO.setCreatedAt(user.getCreatedAt());
        userVO.setUpdatedAt(user.getUpdatedAt());
        return userVO;
    }
}

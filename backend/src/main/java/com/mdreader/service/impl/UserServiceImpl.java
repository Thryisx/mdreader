package com.mdreader.service.impl;

import com.mdreader.common.PageResult;
import com.mdreader.common.constant.SystemConstants;
import com.mdreader.common.context.UserContext;
import com.mdreader.common.exception.BusinessException;
import com.mdreader.entity.dto.CreateUserDTO;
import com.mdreader.entity.dto.UpdateUserDTO;
import com.mdreader.entity.dto.UserPageQueryDTO;
import com.mdreader.entity.entity.UserDO;
import com.mdreader.entity.vo.UserVO;
import com.mdreader.mapper.UserMapper;
import com.mdreader.service.UserService;
import com.mdreader.utils.PageUtil;
import com.mdreader.utils.PasswordPolicy;
import com.mdreader.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户查询与后台管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserMapper userMapper;

    /**
     * 根据 ID 查询用户信息。
     *
     * @param id 用户 ID
     * @return 用户信息
     * 策略：校验 ID 后调用 mapper 单表查询，不存在时抛出 404 业务异常，最后转换为 VO
     */
    @Override
    public UserVO getById(Long id) {
        if (id == null) {
            throw new BusinessException(400, "用户 ID 不能为空");
        }
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toVO(user);
    }

    /**
     * 分页查询用户。
     *
     * @param query 分页及筛选条件
     * @return 用户分页结果
     * 策略：规范化分页参数，先统计总数，总数大于 0 时查询当前页并转换为 VO
     */
    @Override
    public PageResult<UserVO> page(UserPageQueryDTO query) {
        UserPageQueryDTO safeQuery = query == null ? new UserPageQueryDTO() : query;
        long pageNum = PageUtil.normalizePageNum(safeQuery.getPageNum());
        long pageSize = PageUtil.normalizePageSize(safeQuery.getPageSize());
        long offset = PageUtil.calculateOffset(pageNum, pageSize);

        long total = userMapper.countPage(safeQuery);
        List<UserVO> records = Collections.emptyList();
        if (total > 0) {
            records = userMapper.selectPage(safeQuery, offset, pageSize).stream()
                    .map(this::toVO)
                    .collect(Collectors.toList());
        }
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    /**
     * 管理员新增用户。
     *
     * @param createUserDTO 新增参数
     * @return 新增后的用户信息
     * 策略：校验用户名、密码、邮箱、昵称、角色和状态，检查唯一性，使用 PBKDF2 保存密码
     */
    @Override
    public UserVO create(CreateUserDTO createUserDTO) {
        if (createUserDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String username = trimToNull(createUserDTO.getUsername());
        String password = createUserDTO.getPassword();
        String nickname = trimToNull(createUserDTO.getNickname());
        String email = trimToNull(createUserDTO.getEmail());
        String role = normalizeRole(createUserDTO.getRole(), SystemConstants.ROLE_USER);
        Integer status = createUserDTO.getStatus() == null ? 1 : createUserDTO.getStatus();

        validateUsername(username);
        validatePassword(password);
        validateNickname(nickname);
        validateEmail(email);
        validateRole(role);
        validateStatus(status);

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
        user.setRole(role);
        user.setStatus(status);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "用户名或邮箱已存在");
        }
        return getById(user.getId());
    }

    /**
     * 管理员修改用户资料。
     *
     * @param id            用户 ID
     * @param updateUserDTO 修改参数
     * @return 修改后的用户信息
     * 策略：校验目标用户存在，动态更新昵称、邮箱、角色和状态；禁止管理员禁用或降级自己
     */
    @Override
    public UserVO update(Long id, UpdateUserDTO updateUserDTO) {
        if (id == null) {
            throw new BusinessException(400, "用户 ID 不能为空");
        }
        if (updateUserDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        UserDO existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (updateUserDTO.getNickname() == null
                && updateUserDTO.getEmail() == null
                && updateUserDTO.getRole() == null
                && updateUserDTO.getStatus() == null) {
            throw new BusinessException(400, "至少提供一个需要修改的字段");
        }

        String nickname = updateUserDTO.getNickname();
        String email = updateUserDTO.getEmail();
        String role = updateUserDTO.getRole() == null ? null : normalizeRole(updateUserDTO.getRole(), null);
        Integer status = updateUserDTO.getStatus();

        if (nickname != null) {
            nickname = nickname.trim();
            validateNickname(nickname);
        }
        if (email != null) {
            email = email.trim();
            validateEmail(email);
            if (!email.isEmpty() && userMapper.countByEmailExcludingId(email, id) > 0) {
                throw new BusinessException(409, "邮箱已被使用");
            }
        }
        if (role != null) {
            validateRole(role);
        }
        if (status != null) {
            validateStatus(status);
        }

        Long currentUserId = UserContext.getUserId();
        if (id.equals(currentUserId)) {
            if (role != null && !SystemConstants.ROLE_ADMIN.equals(role)) {
                throw new BusinessException(400, "不能修改当前登录管理员的角色");
            }
            if (status != null && status == 0) {
                throw new BusinessException(400, "不能禁用当前登录用户");
            }
        }

        boolean invalidateTokens = role != null && !role.equals(existing.getRole())
                || status != null && !status.equals(existing.getStatus());
        boolean removesEnabledAdmin = SystemConstants.ROLE_ADMIN.equals(existing.getRole())
                && Integer.valueOf(1).equals(existing.getStatus())
                && (role != null && !SystemConstants.ROLE_ADMIN.equals(role)
                || status != null && status == 0);
        if (removesEnabledAdmin && userMapper.countEnabledAdmins() <= 1) {
            throw new BusinessException(400, "系统至少需要保留一个启用中的管理员");
        }

        UserDO user = new UserDO();
        user.setId(id);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(status);
        try {
            userMapper.updateAdmin(user);
            if (invalidateTokens) {
                userMapper.incrementTokenVersion(id);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "邮箱已被使用");
        }
        return getById(id);
    }

    /**
     * 管理员启用/禁用用户。
     *
     * @param id     用户 ID
     * @param status 账号状态：1 启用，0 禁用
     * @return 修改后的用户信息
     * 策略：校验状态值，禁止管理员禁用自己
     */
    @Override
    public UserVO updateStatus(Long id, Integer status) {
        if (id == null) {
            throw new BusinessException(400, "用户 ID 不能为空");
        }
        validateStatus(status);
        UserDO existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (id.equals(UserContext.getUserId()) && status == 0) {
            throw new BusinessException(400, "不能禁用当前登录用户");
        }
        if (status == 0
                && SystemConstants.ROLE_ADMIN.equals(existing.getRole())
                && Integer.valueOf(1).equals(existing.getStatus())
                && userMapper.countEnabledAdmins() <= 1) {
            throw new BusinessException(400, "系统至少需要保留一个启用中的管理员");
        }
        userMapper.updateStatus(id, status);
        return getById(id);
    }

    /**
     * 管理员重置用户密码。
     *
     * @param id          用户 ID
     * @param newPassword 新密码
     * 策略：校验统一密码策略后使用 PBKDF2 哈希更新，并递增 Token 版本使旧 Token 失效
     */
    @Override
    public void resetPassword(Long id, String newPassword) {
        if (id == null) {
            throw new BusinessException(400, "用户 ID 不能为空");
        }
        validatePassword(newPassword);
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.resetPassword(id, PasswordUtil.hash(newPassword));
    }

    /**
     * 管理员删除用户。
     *
     * @param id 用户 ID
     * 策略：禁止删除当前登录管理员；目标用户存在文件时由外键约束阻止删除
     */
    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "用户 ID 不能为空");
        }
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (id.equals(UserContext.getUserId())) {
            throw new BusinessException(400, "不能删除当前登录用户");
        }
        UserDO existing = userMapper.selectById(id);
        if (SystemConstants.ROLE_ADMIN.equals(existing.getRole())
                && Integer.valueOf(1).equals(existing.getStatus())
                && userMapper.countEnabledAdmins() <= 1) {
            throw new BusinessException(400, "系统至少需要保留一个启用中的管理员");
        }
        try {
            userMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(409, "该用户存在文件，无法删除");
        }
    }

    /**
     * 将用户持久化对象转换为展示对象。
     *
     * @param user 用户持久化对象
     * @return 用户展示对象
     * 策略：逐字段复制，主动排除 password_hash 等敏感字段
     */
    private UserVO toVO(UserDO user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setMustChangePassword(Integer.valueOf(1).equals(user.getMustChangePassword()));
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }

    /**
     * 校验用户名格式。
     *
     * @param username 用户名
     * 策略：要求 3-50 位字母、数字或下划线
     */
    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(400, "用户名必须为3-50位字母、数字或下划线");
        }
    }

    /**
     * 校验密码是否符合统一策略。
     *
     * @param password 原始密码
     * 策略：要求 10 位且包含 8 位数字和 2 位英文字母
     */
    private void validatePassword(String password) {
        PasswordPolicy.validate(password);
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
     * 策略：允许为空；非空时使用基础邮箱正则并限制长度
     */
    private void validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            return;
        }
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
    }

    /**
     * 校验角色值。
     *
     * @param role 角色
     * 策略：仅允许 ADMIN / USER
     */
    private void validateRole(String role) {
        if (!SystemConstants.ROLE_ADMIN.equals(role) && !SystemConstants.ROLE_USER.equals(role)) {
            throw new BusinessException(400, "角色必须为 ADMIN 或 USER");
        }
    }

    /**
     * 校验账号状态。
     *
     * @param status 状态
     * 策略：仅允许 0 或 1
     */
    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "账号状态必须为 0 或 1");
        }
    }

    /**
     * 规范化角色值。
     *
     * @param role         原始角色
     * @param defaultValue 为空时的默认角色
     * @return 规范化后的角色
     * 策略：去除空白并转大写；为空时返回默认值
     */
    private String normalizeRole(String role, String defaultValue) {
        if (role == null || role.isBlank()) {
            return defaultValue;
        }
        return role.trim().toUpperCase(Locale.ROOT);
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
}

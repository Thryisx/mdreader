package com.mdreader.controller;

import com.mdreader.common.PageResult;
import com.mdreader.common.Result;
import com.mdreader.common.annotation.RequireAdmin;
import com.mdreader.entity.dto.CreateUserDTO;
import com.mdreader.entity.dto.ResetPasswordDTO;
import com.mdreader.entity.dto.UpdateUserDTO;
import com.mdreader.entity.dto.UpdateUserStatusDTO;
import com.mdreader.entity.dto.UserPageQueryDTO;
import com.mdreader.entity.vo.UserVO;
import com.mdreader.service.AvatarService;
import com.mdreader.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户查询接口。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    /**
     * 上传或更换当前用户头像。
     *
     * @param file 头像文件
     * @return 统一响应结构中的更新后用户信息
     */
    @PostMapping("/me/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(avatarService.uploadCurrentUserAvatar(file));
    }

    /**
     * 管理员新增用户。
     *
     * @param createUserDTO 新增参数
     * @return 统一响应结构中的新增用户信息
     */
    @PostMapping
    @RequireAdmin
    public Result<UserVO> create(@RequestBody CreateUserDTO createUserDTO) {
        return Result.success(userService.create(createUserDTO));
    }

    /**
     * 管理员修改用户资料。
     *
     * @param id            用户 ID
     * @param updateUserDTO 修改参数
     * @return 统一响应结构中的修改后用户信息
     */
    @PutMapping("/{id}")
    @RequireAdmin
    public Result<UserVO> update(@PathVariable("id") Long id,
                                 @RequestBody UpdateUserDTO updateUserDTO) {
        return Result.success(userService.update(id, updateUserDTO));
    }

    /**
     * 管理员启用/禁用用户。
     *
     * @param id                  用户 ID
     * @param updateUserStatusDTO 状态参数
     * @return 统一响应结构中的修改后用户信息
     */
    @PatchMapping("/{id}/status")
    @RequireAdmin
    public Result<UserVO> updateStatus(@PathVariable("id") Long id,
                                       @RequestBody UpdateUserStatusDTO updateUserStatusDTO) {
        Integer status = updateUserStatusDTO == null ? null : updateUserStatusDTO.getStatus();
        return Result.success(userService.updateStatus(id, status));
    }

    /**
     * 管理员重置用户密码。
     *
     * @param id               用户 ID
     * @param resetPasswordDTO 新密码参数
     * @return 统一成功响应
     */
    @PostMapping("/{id}/reset-password")
    @RequireAdmin
    public Result<Void> resetPassword(@PathVariable("id") Long id,
                                      @RequestBody ResetPasswordDTO resetPasswordDTO) {
        String password = resetPasswordDTO == null ? null : resetPasswordDTO.getPassword();
        userService.resetPassword(id, password);
        return Result.success(null);
    }

    /**
     * 管理员删除用户。
     *
     * @param id 用户 ID
     * @return 统一成功响应
     */
    @DeleteMapping("/{id}")
    @RequireAdmin
    public Result<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return Result.success(null);
    }

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 统一响应结构中的用户信息
     */
    @GetMapping("/{id}")
    @RequireAdmin
    public Result<UserVO> getById(@PathVariable("id") Long id) {
        return Result.success(userService.getById(id));
    }

    /**
     * 分页查询用户。
     *
     * @param query 分页及筛选条件
     * @return 统一响应结构中的用户分页结果
     */
    @GetMapping
    @RequireAdmin
    public Result<PageResult<UserVO>> page(@ModelAttribute UserPageQueryDTO query) {
        return Result.success(userService.page(query));
    }
}

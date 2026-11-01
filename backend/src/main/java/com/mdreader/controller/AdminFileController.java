package com.mdreader.controller;

import com.mdreader.common.PageResult;
import com.mdreader.common.Result;
import com.mdreader.common.annotation.RequireAdmin;
import com.mdreader.entity.dto.AdminFilePageQueryDTO;
import com.mdreader.entity.vo.AdminFileVO;
import com.mdreader.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员文件管理接口。
 */
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
@RequireAdmin
public class AdminFileController {

    private final FileService fileService;

    /**
     * 分页查询全量文件。
     *
     * @param query 分页及用户、可见性、状态、关键字筛选条件
     * @return 统一响应结构中的文件分页结果
     */
    @GetMapping
    public Result<PageResult<AdminFileVO>> page(@ModelAttribute AdminFilePageQueryDTO query) {
        return Result.success(fileService.pageAdmin(query));
    }
}

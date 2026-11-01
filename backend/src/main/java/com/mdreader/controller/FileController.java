package com.mdreader.controller;

import com.mdreader.common.PageResult;
import com.mdreader.common.Result;
import com.mdreader.common.annotation.OptionalAuth;
import com.mdreader.common.exception.BusinessException;
import com.mdreader.entity.dto.CreateFileDTO;
import com.mdreader.entity.dto.CreateFolderDTO;
import com.mdreader.entity.dto.FilePageQueryDTO;
import com.mdreader.entity.dto.MoveNodeDTO;
import com.mdreader.entity.dto.RenameNodeDTO;
import com.mdreader.entity.dto.UpdateFileDTO;
import com.mdreader.entity.vo.CurrentUserFileListVO;
import com.mdreader.entity.vo.FileCategoryVO;
import com.mdreader.entity.vo.FileContentVO;
import com.mdreader.entity.vo.FileDetailVO;
import com.mdreader.entity.vo.FileTreeNodeVO;
import com.mdreader.entity.vo.FileVO;
import com.mdreader.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件查询接口。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 当前用户新建文件。
     *
     * @param createFileDTO 新建参数
     * @return 统一响应结构中的新建文件详情
     */
    @PostMapping
    public Result<FileDetailVO> create(@RequestBody CreateFileDTO createFileDTO) {
        return Result.success(fileService.createCurrentUserFile(createFileDTO));
    }

    /**
     * 当前用户新建文件夹。
     *
     * @param createFolderDTO 新建文件夹参数
     * @return 统一响应结构中的文件夹节点
     * 策略：父节点为 null 时在根目录创建；同级重名返回 409
     */
    @PostMapping("/folders")
    public Result<FileTreeNodeVO> createFolder(@RequestBody CreateFolderDTO createFolderDTO) {
        return Result.success(fileService.createFolder(createFolderDTO));
    }

    /**
     * 查询当前用户的文件树。
     *
     * @return 统一响应结构中的根节点列表
     * 策略：返回当前用户全部文件与文件夹，文件夹在前、名称升序
     */
    @GetMapping("/tree")
    public Result<List<FileTreeNodeVO>> tree() {
        return Result.success(fileService.getTree());
    }

    /**
     * 当前用户重命名文件或文件夹。
     *
     * @param id            节点 ID
     * @param renameNodeDTO 重命名参数
     * @return 统一响应结构中的重命名后节点
     * 策略：仅所有者可操作；同级重名返回 409
     */
    @PatchMapping("/{id}/rename")
    public Result<FileTreeNodeVO> rename(@PathVariable("id") Long id,
                                         @RequestBody RenameNodeDTO renameNodeDTO) {
        return Result.success(fileService.renameNode(id, renameNodeDTO));
    }

    /**
     * 当前用户移动文件或文件夹。
     *
     * @param id          节点 ID
     * @param moveNodeDTO 移动参数
     * @return 统一响应结构中的移动后节点
     * 策略：parentId 为 null 表示移动到根目录；禁止移入自身或后代
     */
    @PatchMapping("/{id}/move")
    public Result<FileTreeNodeVO> move(@PathVariable("id") Long id,
                                       @RequestBody MoveNodeDTO moveNodeDTO) {
        return Result.success(fileService.moveNode(id, moveNodeDTO));
    }

    /**
     * 当前用户删除文件或文件夹。
     *
     * @param id 节点 ID
     * @return 统一响应结构中的空数据
     * 策略：文件夹递归删除全部后代节点；仅所有者可操作
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        fileService.deleteNode(id);
        return Result.success(null);
    }

    /**
     * 当前用户保存文件。
     *
     * @param id            文件 ID
     * @param updateFileDTO 保存参数
     * @return 统一响应结构中的保存后文件详情
     */
    @PutMapping("/{id}")
    public Result<FileDetailVO> update(@PathVariable("id") Long id,
                                       @RequestBody UpdateFileDTO updateFileDTO) {
        return Result.success(fileService.updateCurrentUserFile(id, updateFileDTO));
    }

    /**
     * 读取 Markdown 正文。
     *
     * @param id 文件 ID
     * @return Markdown 文本；外部存储时重定向到 mdUrl
     * 策略：内部 content 非空直接返回 text/markdown；content 为空且 mdUrl 为 http(s) 时 302 重定向
     */
    @GetMapping(value = "/{id}/content", produces = "text/markdown;charset=UTF-8")
    @OptionalAuth
    public ResponseEntity<String> content(@PathVariable("id") Long id) {
        FileContentVO content = fileService.getContent(id);
        if (content.getContent() != null) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                    .header("Cache-Control", "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(content.getContent());
        }
        String mdUrl = content.getMdUrl();
        if (mdUrl != null && (mdUrl.startsWith("http://") || mdUrl.startsWith("https://"))) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(mdUrl))
                    .header("Cache-Control", "no-store")
                    .build();
        }
        throw new BusinessException(404, "文件内容不存在");
    }

    /**
     * 查询当前登录用户的文件列表。
     *
     * @param page 页码，从 1 开始；为空时使用默认值
     * @param size 每页条数，范围 1-20；为空时使用默认值
     * @return 统一响应结构中的当前用户文件列表
     */
    @GetMapping("/list")
    public Result<CurrentUserFileListVO> listCurrentUser(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.success(fileService.listCurrentUserFiles(page, size));
    }

    /**
     * 根据 ID 查询公开已发布文件详情。
     *
     * @param id 文件 ID
     * @return 统一响应结构中的文件详情
     */
    @GetMapping("/{id}")
    @OptionalAuth
    public Result<FileDetailVO> getById(@PathVariable("id") Long id) {
        return Result.success(fileService.getById(id));
    }

    /**
     * 查询公开已发布文件的分类及数量。
     *
     * @return 统一响应结构中的分类计数列表
     * 策略：匿名可读；未分类项的 category 为 null
     */
    @GetMapping("/categories")
    @OptionalAuth
    public Result<List<FileCategoryVO>> categories() {
        return Result.success(fileService.listPublicCategories());
    }

    /**
     * 分页查询公开已发布文件。
     *
     * @param query 分页及标题搜索条件
     * @return 统一响应结构中的文件分页结果
     */
    @GetMapping
    @OptionalAuth
    public Result<PageResult<FileVO>> page(@ModelAttribute FilePageQueryDTO query) {
        return Result.success(fileService.pagePublic(query));
    }

}

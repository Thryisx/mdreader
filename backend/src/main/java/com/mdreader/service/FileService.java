package com.mdreader.service;

import com.mdreader.common.PageResult;
import com.mdreader.entity.dto.AdminFilePageQueryDTO;
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
import com.mdreader.entity.vo.AdminFileVO;
import com.mdreader.entity.vo.FileVO;

import java.util.List;

/**
 * 文件查询服务。
 */
public interface FileService {

    /**
     * 根据 ID 查询公开已发布文件详情。
     *
     * @param id 文件 ID
     * @return 文件详情
     * 策略：所有者可读取自己任意状态的文件；其他用户仅能读取 PUBLIC+PUBLISHED 文件，并将 img_urls JSON 解析为图片列表
     */
    FileDetailVO getById(Long id);

    /**
     * 分页查询公开已发布文件。
     *
     * @param query 分页及标题搜索条件
     * @return 文件分页结果
     * 策略：固定 PUBLIC+PUBLISHED 条件，支持标题模糊搜索，先统计总数再分页查询
     */
    PageResult<FileVO> pagePublic(FilePageQueryDTO query);

    /**
     * 查询公开已发布文件的分类及数量。
     *
     * @return 分类计数列表；未分类项的 category 为 null
     * 策略：固定 PUBLIC+PUBLISHED 条件，按分类分组统计，供公开文章页展示种类与计数
     */
    List<FileCategoryVO> listPublicCategories();

    /**
     * 查询当前登录用户的文件列表。
     *
     * @param page 页码，从 1 开始；为空时使用默认值
     * @param size 每页条数，范围 1-20；为空时使用默认值
     * @return 当前用户文件列表及总数
     * 策略：从 UserContext 获取用户 ID，不限制可见性和状态，按更新时间倒序分页
     */
    CurrentUserFileListVO listCurrentUserFiles(Integer page, Integer size);

    /**
     * 当前用户新建文件。
     *
     * @param createFileDTO 新建参数
     * @return 新建后的文件详情
     * 策略：从 UserContext 获取所有者，校验标题、可见性和状态，正文存入 content 字段并生成内部正文 URL
     */
    FileDetailVO createCurrentUserFile(CreateFileDTO createFileDTO);

    /**
     * 当前用户保存文件。
     *
     * @param id            文件 ID
     * @param updateFileDTO 保存参数
     * @return 保存后的文件详情
     * 策略：校验文件存在且属于当前用户，动态更新标题、正文、可见性和状态
     */
    FileDetailVO updateCurrentUserFile(Long id, UpdateFileDTO updateFileDTO);

    /**
     * 读取文件 Markdown 正文。
     *
     * @param id 文件 ID
     * @return 正文或外部 URL
     * 策略：所有者可读取私有/草稿文件；其他用户仅能读取 PUBLIC+PUBLISHED 文件
     */
    FileContentVO getContent(Long id);

    /**
     * 管理员分页查询全量文件。
     *
     * @param query 分页及用户、可见性、状态、关键字筛选条件
     * @return 文件分页结果
     * 策略：不限制可见性和状态，先统计总数再分页查询，并解析封面
     */
    PageResult<AdminFileVO> pageAdmin(AdminFilePageQueryDTO query);

    /**
     * 查询当前用户的文件树。
     *
     * @return 根节点列表，文件夹在前、名称升序
     * 策略：一次性读取当前用户全部节点，在内存中按 parent_id 组装树并计算 path
     */
    List<FileTreeNodeVO> getTree();

    /**
     * 在当前用户目录下新建文件夹。
     *
     * @param createFolderDTO 新建参数
     * @return 新建的文件夹节点
     * 策略：校验父节点归属与类型、同级重名后写入 node_type=FOLDER 的记录
     */
    FileTreeNodeVO createFolder(CreateFolderDTO createFolderDTO);

    /**
     * 重命名当前用户的文件或文件夹。
     *
     * @param id             节点 ID
     * @param renameNodeDTO  重命名参数
     * @return 重命名后的节点
     * 策略：校验归属、名称长度与同级重名后更新 title；文件同时更新 original_name
     */
    FileTreeNodeVO renameNode(Long id, RenameNodeDTO renameNodeDTO);

    /**
     * 移动当前用户的文件或文件夹。
     *
     * @param id          节点 ID
     * @param moveNodeDTO 移动参数
     * @return 移动后的节点
     * 策略：校验归属、目标文件夹存在性、禁止移入自身或后代、同级重名后更新 parent_id
     */
    FileTreeNodeVO moveNode(Long id, MoveNodeDTO moveNodeDTO);

    /**
     * 删除当前用户的文件或文件夹。
     *
     * @param id 节点 ID
     * 策略：校验归属；文件夹递归删除全部后代节点，文件直接删除
     */
    void deleteNode(Long id);
}

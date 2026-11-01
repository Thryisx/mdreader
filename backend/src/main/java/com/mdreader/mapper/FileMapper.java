package com.mdreader.mapper;

import com.mdreader.entity.dto.AdminFilePageQueryDTO;
import com.mdreader.entity.dto.FilePageQueryDTO;
import com.mdreader.entity.entity.FileDO;
import com.mdreader.entity.vo.FileCategoryVO;
import com.mdreader.entity.vo.FileListItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件数据访问接口。
 */
public interface FileMapper {

    /**
     * 根据 ID 查询公开已发布文件。
     *
     * @param id 文件 ID
     * @return 文件数据；不存在或未公开时返回 null
     * 策略：按主键查询，并附加 visibility=PUBLIC、status=PUBLISHED 条件
     */
    FileDO selectPublicById(@Param("id") Long id);

    /**
     * 根据 ID 查询文件，不限制可见性和状态。
     *
     * @param id 文件 ID
     * @return 文件数据；不存在时返回 null
     * 策略：按主键单表查询，供所有者读取私有/草稿文件及保存权限校验
     */
    FileDO selectById(@Param("id") Long id);

    /**
     * 根据 ID 查询 Markdown 正文。
     *
     * @param id 文件 ID
     * @return 包含 content、md_url、user_id、visibility、status 的文件数据；不存在时返回 null
     * 策略：只查询正文读取所需字段，避免详情接口传输大字段
     */
    FileDO selectContentById(@Param("id") Long id);

    /**
     * 统计符合条件的公开已发布文件数量。
     *
     * @param query 标题搜索条件
     * @return 文件总数
     * 策略：固定 PUBLIC+PUBLISHED 条件，可选标题模糊匹配，执行 COUNT(*)
     */
    long countPublic(@Param("query") FilePageQueryDTO query);

    /**
     * 分页查询公开已发布文件。
     *
     * @param query  标题搜索条件
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 当前页文件列表；无数据时返回空列表
     * 策略：固定 PUBLIC+PUBLISHED 条件，可选标题模糊匹配，按创建时间倒序，使用 LIMIT offset, limit 分页
     */
    List<FileDO> selectPublicPage(@Param("query") FilePageQueryDTO query,
                                  @Param("offset") long offset,
                                  @Param("limit") long limit);

    /**
     * 统计公开已发布文件的分类及数量。
     *
     * @return 分类计数；未分类的 category 为 null
     * 策略：固定 PUBLIC+PUBLISHED 条件，按去空白后的 category 分组统计
     */
    List<FileCategoryVO> selectPublicCategories();

    /**
     * 统计指定用户的文件数量。
     *
     * @param userId 用户 ID
     * @return 文件总数
     * 策略：按 user_id 单表统计，不限制可见性和状态
     */
    long countByUserId(@Param("userId") Long userId);

    /**
     * 分页查询指定用户的文件列表项。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 当前页文件列表；无数据时返回空列表
     * 策略：只查询列表展示字段，按 updated_at DESC、id DESC 排序，使用 LIMIT offset, limit 分页
     */
    List<FileListItemVO> selectListByUserId(@Param("userId") Long userId,
                                            @Param("offset") long offset,
                                            @Param("limit") long limit);

    /**
     * 新增文件。
     *
     * @param file 文件数据
     * @return 影响行数
     * 策略：插入所有者、标题、正文、大小、可见性和状态，并回填自增主键
     */
    int insertFile(FileDO file);

    /**
     * 更新文件内容与元数据。
     *
     * @param file 文件数据，至少包含 id
     * @return 影响行数
     * 策略：动态更新非 null 字段，同时刷新 updated_at
     */
    int updateFile(FileDO file);

    /**
     * 更新文件 Markdown 访问 URL。
     *
     * @param id    文件 ID
     * @param mdUrl Markdown 访问 URL
     * @return 影响行数
     * 策略：文件创建后根据主键生成内部正文访问地址
     */
    int updateMdUrl(@Param("id") Long id, @Param("mdUrl") String mdUrl);

    /**
     * 统计管理员筛选条件下的文件数量。
     *
     * @param query 用户、可见性、状态和关键字筛选条件
     * @return 文件总数
     * 策略：动态拼接可选条件，不限制可见性和状态
     */
    long countAdmin(@Param("query") AdminFilePageQueryDTO query);

    /**
     * 管理员分页查询文件。
     *
     * @param query  用户、可见性、状态和关键字筛选条件
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 当前页文件列表；无数据时返回空列表
     * 策略：动态拼接可选条件，按更新时间倒序、ID 倒序，使用 LIMIT offset, limit 分页
     */
    List<FileDO> selectAdminPage(@Param("query") AdminFilePageQueryDTO query,
                                 @Param("offset") long offset,
                                 @Param("limit") long limit);

    /**
     * 查询指定用户的全部文件与文件夹节点。
     *
     * @param userId 用户 ID
     * @return 该用户的全部节点；无数据时返回空列表
     * 策略：按 user_id 单表查询，不做分页，供 Service 在内存中组装文件树
     */
    List<FileDO> selectAllByUserId(@Param("userId") Long userId);

    /**
     * 统计同级节点中同名节点的数量。
     *
     * @param userId    用户 ID
     * @param parentId  父节点 ID；null 表示根目录
     * @param title     节点名称
     * @param excludeId 需要排除的节点 ID，可为 null
     * @return 同名节点数量
     * 策略：限定 user_id 与 parent_id（null 时使用 IS NULL），并可选排除自身，用于重名校验
     */
    int countSiblingByTitle(@Param("userId") Long userId,
                            @Param("parentId") Long parentId,
                            @Param("title") String title,
                            @Param("excludeId") Long excludeId);

    /**
     * 移动文件或文件夹到新的父节点。
     *
     * @param id       节点 ID
     * @param userId   用户 ID
     * @param parentId 目标父节点 ID；null 表示根目录
     * @return 影响行数
     * 策略：按 id + user_id 更新 parent_id，并刷新 updated_at，避免跨用户移动
     */
    int updateParent(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("parentId") Long parentId);

    /**
     * 删除指定用户的单个节点。
     *
     * @param id     节点 ID
     * @param userId 用户 ID
     * @return 影响行数
     * 策略：按 id + user_id 删除；文件夹的递归删除由 Service 收集后代后逐个调用
     */
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}

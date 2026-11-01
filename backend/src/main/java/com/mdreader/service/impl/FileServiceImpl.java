package com.mdreader.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdreader.common.PageResult;
import com.mdreader.common.context.UserContext;
import com.mdreader.common.exception.BusinessException;
import com.mdreader.entity.dto.AdminFilePageQueryDTO;
import com.mdreader.entity.dto.CreateFileDTO;
import com.mdreader.entity.dto.CreateFolderDTO;
import com.mdreader.entity.dto.FilePageQueryDTO;
import com.mdreader.entity.dto.MoveNodeDTO;
import com.mdreader.entity.dto.RenameNodeDTO;
import com.mdreader.entity.dto.UpdateFileDTO;
import com.mdreader.entity.entity.FileDO;
import com.mdreader.entity.vo.AdminFileVO;
import com.mdreader.entity.vo.CurrentUserFileListVO;
import com.mdreader.entity.vo.FileCategoryVO;
import com.mdreader.entity.vo.FileContentVO;
import com.mdreader.entity.vo.FileDetailVO;
import com.mdreader.entity.vo.FileListItemVO;
import com.mdreader.entity.vo.FileTreeNodeVO;
import com.mdreader.entity.vo.FileVO;
import com.mdreader.entity.vo.ImageInfoVO;
import com.mdreader.mapper.FileMapper;
import com.mdreader.service.FileService;
import com.mdreader.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件查询与写入服务实现。
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final TypeReference<List<ImageInfoVO>> IMAGE_LIST_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;
    private static final int MAX_CATEGORY_LENGTH = 64;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int SUMMARY_SCAN_LENGTH = 4000;
    private static final long MAX_CONTENT_BYTES = 10L * 1024 * 1024;
    private static final String DEFAULT_VISIBILITY = "PRIVATE";
    private static final String DEFAULT_STATUS = "DRAFT";
    private static final String STORAGE_PROVIDER_LOCAL = "LOCAL";
    private static final String NODE_TYPE_FILE = "FILE";
    private static final String NODE_TYPE_FOLDER = "FOLDER";
    private static final int MAX_NODE_NAME_LENGTH = 255;
    private static final Set<String> ALLOWED_VISIBILITIES = Set.of("PUBLIC", "PRIVATE");
    private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "PENDING", "PUBLISHED", "REJECTED");

    private final FileMapper fileMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据 ID 查询文件详情。
     *
     * @param id 文件 ID
     * @return 文件详情
     * 策略：匿名用户和其他用户仅能读取 PUBLIC+PUBLISHED 文件；所有者可读取自己任意状态文件
     */
    @Override
    public FileDetailVO getById(Long id) {
        Long userId = UserContext.getUserId();
        if (id == null) {
            throw new BusinessException(400, "文件 ID 不能为空");
        }
        FileDO file = fileMapper.selectById(id);
        if (file == null || !canRead(file, userId)) {
            throw new BusinessException(404, "文件不存在或未公开");
        }
        return toDetailVO(file);
    }

    /**
     * 分页查询公开已发布文件。
     *
     * @param query 分页及标题搜索条件
     * @return 文件分页结果
     * 策略：规范化分页参数，固定 PUBLIC+PUBLISHED 条件，先统计总数，总数大于 0 时查询当前页并转换为 VO
     */
    @Override
    public PageResult<FileVO> pagePublic(FilePageQueryDTO query) {
        FilePageQueryDTO safeQuery = query == null ? new FilePageQueryDTO() : query;
        long pageNum = PageUtil.normalizePageNum(safeQuery.getPageNum());
        long pageSize = PageUtil.normalizePageSize(safeQuery.getPageSize());
        long offset = PageUtil.calculateOffset(pageNum, pageSize);

        long total = fileMapper.countPublic(safeQuery);
        List<FileVO> records = Collections.emptyList();
        if (total > 0) {
            records = fileMapper.selectPublicPage(safeQuery, offset, pageSize).stream()
                    .map(this::toVO)
                    .collect(Collectors.toList());
        }
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询公开已发布文件的分类及数量。
     *
     * @return 分类计数列表；未分类项的 category 为 null
     * 策略：固定 PUBLIC+PUBLISHED 条件分组统计，未分类归入 category 为 null 的一项
     */
    @Override
    public List<FileCategoryVO> listPublicCategories() {
        List<FileCategoryVO> categories = fileMapper.selectPublicCategories();
        return categories == null ? Collections.emptyList() : categories;
    }

    /**
     * 查询当前登录用户的文件列表。
     *
     * @param page 页码，从 1 开始；为空时使用默认值
     * @param size 每页条数，范围 1-20；为空时使用默认值
     * @return 当前用户文件列表及总数
     * 策略：从 UserContext 获取用户 ID，校验分页参数，统计总数后按更新时间倒序查询当前页；不限制可见性和状态
     */
    @Override
    public CurrentUserFileListVO listCurrentUserFiles(Integer page, Integer size) {
        Long userId = requireCurrentUserId();

        int pageNum = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNum < 1) {
            throw new BusinessException(400, "页码不能小于1");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException(400, "每页数量必须在1-20之间");
        }

        long offset = ((long) pageNum - 1) * pageSize;
        long total = fileMapper.countByUserId(userId);
        List<FileListItemVO> records = Collections.emptyList();
        if (total > 0) {
            records = fileMapper.selectListByUserId(userId, offset, pageSize);
        }
        return new CurrentUserFileListVO(total, records);
    }

    /**
     * 当前用户新建文件。
     *
     * @param createFileDTO 新建参数
     * @return 新建后的文件详情
     * 策略：从 UserContext 获取所有者，校验标题、可见性和状态，正文存入 content 字段并生成内部正文 URL
     */
    @Override
    @Transactional
    public FileDetailVO createCurrentUserFile(CreateFileDTO createFileDTO) {
        Long userId = requireCurrentUserId();
        if (createFileDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }

        String title = normalizeTitle(createFileDTO.getTitle());
        String content = createFileDTO.getContent() == null ? "" : createFileDTO.getContent();
        validateContent(content);
        String visibility = normalizeVisibility(createFileDTO.getVisibility(), DEFAULT_VISIBILITY);
        String status = normalizeStatus(createFileDTO.getStatus(), DEFAULT_STATUS);
        String originalName = normalizeOriginalName(createFileDTO.getOriginalName(), title);
        String category = normalizeCategory(createFileDTO.getCategory());
        String summary = normalizeSummary(createFileDTO.getSummary());
        if (summary == null) {
            summary = generateSummary(content);
        }
        Long parentId = resolveParentId(userId, createFileDTO.getParentId());
        assertSiblingNameAvailable(userId, parentId, title, null);

        FileDO file = new FileDO();
        file.setUserId(userId);
        file.setParentId(parentId);
        file.setNodeType(NODE_TYPE_FILE);
        file.setTitle(title);
        file.setOriginalName(originalName);
        file.setContent(content);
        file.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        file.setVisibility(visibility);
        file.setStatus(status);
        file.setCategory(category);
        file.setSummary(summary);
        file.setStorageProvider(STORAGE_PROVIDER_LOCAL);
        file.setMdUrl("");
        try {
            fileMapper.insertFile(file);
            fileMapper.updateMdUrl(file.getId(), buildContentUrl(file.getId()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "同级目录下已存在同名文件或文件夹");
        }
        return getById(file.getId());
    }

    /**
     * 当前用户保存文件。
     *
     * @param id            文件 ID
     * @param updateFileDTO 保存参数
     * @return 保存后的文件详情
     * 策略：校验文件存在且属于当前用户，动态更新标题、正文、可见性和状态
     */
    @Override
    public FileDetailVO updateCurrentUserFile(Long id, UpdateFileDTO updateFileDTO) {
        Long userId = requireCurrentUserId();
        if (id == null) {
            throw new BusinessException(400, "文件 ID 不能为空");
        }
        FileDO existing = fileMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文件不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "无权修改该文件");
        }
        if (updateFileDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        if (updateFileDTO.getTitle() == null
                && updateFileDTO.getOriginalName() == null
                && updateFileDTO.getContent() == null
                && updateFileDTO.getCategory() == null
                && updateFileDTO.getSummary() == null
                && updateFileDTO.getVisibility() == null
                && updateFileDTO.getStatus() == null) {
            throw new BusinessException(400, "至少提供一个需要修改的字段");
        }

        FileDO patch = new FileDO();
        patch.setId(id);
        if (updateFileDTO.getTitle() != null) {
            String title = normalizeTitle(updateFileDTO.getTitle());
            if (!title.equals(existing.getTitle())) {
                assertSiblingNameAvailable(userId, existing.getParentId(), title, id);
            }
            patch.setTitle(title);
        }
        if (updateFileDTO.getOriginalName() != null) {
            String originalName = updateFileDTO.getOriginalName().trim();
            if (originalName.length() > MAX_ORIGINAL_NAME_LENGTH) {
                throw new BusinessException(400, "原始文件名不能超过255个字符");
            }
            patch.setOriginalName(originalName);
        }
        if (updateFileDTO.getContent() != null) {
            validateContent(updateFileDTO.getContent());
            patch.setContent(updateFileDTO.getContent());
            patch.setFileSize((long) updateFileDTO.getContent().getBytes(StandardCharsets.UTF_8).length);
        }
        if (updateFileDTO.getCategory() != null) {
            patch.setCategory(normalizeCategoryForUpdate(updateFileDTO.getCategory()));
        }
        if (updateFileDTO.getSummary() != null) {
            String summary = normalizeSummary(updateFileDTO.getSummary());
            if (summary == null) {
                String source = updateFileDTO.getContent() != null
                        ? updateFileDTO.getContent()
                        : loadExistingContent(id);
                summary = generateSummary(source);
            }
            patch.setSummary(summary == null ? "" : summary);
        } else if (updateFileDTO.getContent() != null) {
            String summary = generateSummary(updateFileDTO.getContent());
            patch.setSummary(summary == null ? "" : summary);
        }
        if (updateFileDTO.getVisibility() != null) {
            patch.setVisibility(normalizeVisibility(updateFileDTO.getVisibility(), null));
        }
        if (updateFileDTO.getStatus() != null) {
            patch.setStatus(normalizeStatus(updateFileDTO.getStatus(), null));
        }

        try {
            fileMapper.updateFile(patch);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "同级目录下已存在同名文件或文件夹");
        }
        return getById(id);
    }

    /**
     * 读取文件 Markdown 正文。
     *
     * @param id 文件 ID
     * @return 正文或外部 URL
     * 策略：匿名用户和其他用户仅能读取 PUBLIC+PUBLISHED 文件；所有者可读取自己的私有/草稿文件
     */
    @Override
    public FileContentVO getContent(Long id) {
        Long userId = UserContext.getUserId();
        if (id == null) {
            throw new BusinessException(400, "文件 ID 不能为空");
        }
        FileDO file = fileMapper.selectContentById(id);
        if (file == null || !canRead(file, userId)) {
            throw new BusinessException(404, "文件不存在或未公开");
        }
        if (!NODE_TYPE_FILE.equals(file.getNodeType())) {
            throw new BusinessException(400, "文件夹没有 Markdown 正文");
        }
        FileContentVO vo = new FileContentVO();
        vo.setContent(file.getContent());
        vo.setMdUrl(file.getMdUrl());
        return vo;
    }

    /**
     * 管理员分页查询全量文件。
     *
     * @param query 分页及用户、可见性、状态、关键字筛选条件
     * @return 管理员文件元数据分页结果
     * 策略：不限制可见性和状态，先统计总数再分页查询；仅返回元数据，不返回 mdUrl 或正文
     */
    @Override
    public PageResult<AdminFileVO> pageAdmin(AdminFilePageQueryDTO query) {
        AdminFilePageQueryDTO safeQuery = query == null ? new AdminFilePageQueryDTO() : query;
        if (safeQuery.getVisibility() != null && !safeQuery.getVisibility().isBlank()) {
            safeQuery.setVisibility(normalizeVisibility(safeQuery.getVisibility(), null));
        }
        if (safeQuery.getStatus() != null && !safeQuery.getStatus().isBlank()) {
            safeQuery.setStatus(normalizeStatus(safeQuery.getStatus(), null));
        }
        long pageNum = PageUtil.normalizePageNum(safeQuery.getPageNum());
        long pageSize = PageUtil.normalizePageSize(safeQuery.getPageSize());
        long offset = PageUtil.calculateOffset(pageNum, pageSize);

        long total = fileMapper.countAdmin(safeQuery);
        List<AdminFileVO> records = Collections.emptyList();
        if (total > 0) {
            records = fileMapper.selectAdminPage(safeQuery, offset, pageSize).stream()
                    .map(this::toAdminVO)
                    .collect(Collectors.toList());
        }
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询当前用户的文件树。
     *
     * @return 根节点列表，文件夹在前、名称升序
     * 策略：一次性读取当前用户全部节点，在内存中按 parent_id 组装树并计算 path
     */
    @Override
    public List<FileTreeNodeVO> getTree() {
        Long userId = requireCurrentUserId();
        return buildTree(fileMapper.selectAllByUserId(userId));
    }

    /**
     * 在当前用户目录下新建文件夹。
     *
     * @param createFolderDTO 新建参数
     * @return 新建的文件夹节点
     * 策略：校验父节点归属与类型、同级重名后写入 node_type=FOLDER 的记录
     */
    @Override
    @Transactional
    public FileTreeNodeVO createFolder(CreateFolderDTO createFolderDTO) {
        Long userId = requireCurrentUserId();
        if (createFolderDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String name = normalizeNodeName(createFolderDTO.getName());
        Long parentId = resolveParentId(userId, createFolderDTO.getParentId());
        assertSiblingNameAvailable(userId, parentId, name, null);

        FileDO folder = new FileDO();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setNodeType(NODE_TYPE_FOLDER);
        folder.setTitle(name);
        folder.setOriginalName(name);
        folder.setContent(null);
        folder.setFileSize(0L);
        folder.setVisibility(DEFAULT_VISIBILITY);
        folder.setStatus(DEFAULT_STATUS);
        folder.setStorageProvider(STORAGE_PROVIDER_LOCAL);
        folder.setMdUrl("");
        try {
            fileMapper.insertFile(folder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "同级目录下已存在同名文件或文件夹");
        }
        return toTreeNodeWithPath(userId, requireOwnedNode(folder.getId(), userId));
    }

    /**
     * 重命名当前用户的文件或文件夹。
     *
     * @param id            节点 ID
     * @param renameNodeDTO 重命名参数
     * @return 重命名后的节点
     * 策略：校验归属、名称长度与同级重名后更新 title；文件同时更新 original_name
     */
    @Override
    public FileTreeNodeVO renameNode(Long id, RenameNodeDTO renameNodeDTO) {
        Long userId = requireCurrentUserId();
        FileDO node = requireOwnedNode(id, userId);
        if (renameNodeDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String name = normalizeNodeName(renameNodeDTO.getName());
        assertSiblingNameAvailable(userId, node.getParentId(), name, node.getId());

        FileDO patch = new FileDO();
        patch.setId(node.getId());
        patch.setTitle(name);
        if (NODE_TYPE_FILE.equals(node.getNodeType())) {
            patch.setOriginalName(name);
        }
        try {
            fileMapper.updateFile(patch);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "同级目录下已存在同名文件或文件夹");
        }
        return toTreeNodeWithPath(userId, requireOwnedNode(id, userId));
    }

    /**
     * 移动当前用户的文件或文件夹。
     *
     * @param id          节点 ID
     * @param moveNodeDTO 移动参数
     * @return 移动后的节点
     * 策略：校验归属、目标文件夹存在性、禁止移入自身或后代、同级重名后更新 parent_id
     */
    @Override
    public FileTreeNodeVO moveNode(Long id, MoveNodeDTO moveNodeDTO) {
        Long userId = requireCurrentUserId();
        FileDO node = requireOwnedNode(id, userId);
        if (moveNodeDTO == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        Long targetParentId = resolveParentId(userId, moveNodeDTO.getParentId());
        if (targetParentId != null) {
            if (targetParentId.equals(node.getId())) {
                throw new BusinessException(400, "不能将节点移动到自身");
            }
            if (NODE_TYPE_FOLDER.equals(node.getNodeType())
                    && isDescendantOf(userId, targetParentId, node.getId())) {
                throw new BusinessException(400, "不能将文件夹移动到自己的子目录");
            }
        }

        boolean sameParent = node.getParentId() == null
                ? targetParentId == null
                : node.getParentId().equals(targetParentId);
        if (sameParent) {
            return toTreeNodeWithPath(userId, node);
        }

        assertSiblingNameAvailable(userId, targetParentId, node.getTitle(), node.getId());
        try {
            fileMapper.updateParent(id, userId, targetParentId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "同级目录下已存在同名文件或文件夹");
        }
        return toTreeNodeWithPath(userId, requireOwnedNode(id, userId));
    }

    /**
     * 删除当前用户的文件或文件夹。
     *
     * @param id 节点 ID
     * 策略：校验归属；文件夹递归删除全部后代节点，文件直接删除
     */
    @Override
    @Transactional
    public void deleteNode(Long id) {
        Long userId = requireCurrentUserId();
        FileDO node = requireOwnedNode(id, userId);

        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (FileDO item : fileMapper.selectAllByUserId(userId)) {
            if (item.getParentId() != null) {
                childrenByParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>())
                        .add(item.getId());
            }
        }

        Deque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(node.getId());
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            fileMapper.deleteByIdAndUserId(current, userId);
            queue.addAll(childrenByParent.getOrDefault(current, Collections.emptyList()));
        }
    }

    /**
     * 判断当前用户是否可以读取文件。
     *
     * @param file   文件
     * @param userId 当前用户 ID，匿名访问时为 null
     * @return 是否可读
     * 策略：所有者可读任意状态；匿名用户和其他用户仅可读 PUBLIC+PUBLISHED；管理员不再拥有普通文件接口旁路
     */
    private boolean canRead(FileDO file, Long userId) {
        if (userId != null && userId.equals(file.getUserId())) {
            return true;
        }
        return "PUBLIC".equals(file.getVisibility()) && "PUBLISHED".equals(file.getStatus());
    }

    /**
     * 校验并规范化文件标题。
     *
     * @param title 原始标题
     * @return 去除首尾空白后的标题
     * 策略：标题不能为空且不超过 255 个字符
     */
    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "文件标题不能为空");
        }
        String value = title.trim();
        if (value.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(400, "文件标题不能超过255个字符");
        }
        return value;
    }

    /**
     * 规范化原始文件名。
     *
     * @param originalName 原始文件名，可为空
     * @param title        文件标题
     * @return 原始文件名
     * 策略：为空时使用标题 + .md；超过 255 时回退为标题
     */
    private String normalizeOriginalName(String originalName, String title) {
        String value = originalName == null || originalName.isBlank() ? title + ".md" : originalName.trim();
        if (value.length() > MAX_ORIGINAL_NAME_LENGTH) {
            value = title;
        }
        return value;
    }

    /**
     * 规范化文章分类。
     *
     * @param category 原始分类，可为 null
     * @return 规范化后的分类；为空时返回 null，表示未分类
     * 策略：去首尾空白，超过 64 个字符返回 400；空字符串按未分类处理
     */
    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String value = category.trim();
        if (value.length() > MAX_CATEGORY_LENGTH) {
            throw new BusinessException(400, "文章分类不能超过64个字符");
        }
        return value;
    }

    /**
     * 规范化用于更新的文章分类。
     *
     * @param category 原始分类，可为 null
     * @return 规范化后的分类；清空分类时返回空字符串，由 SQL 写入 NULL
     * 策略：非空时校验长度并去空白，空字符串表示清空为未分类
     */
    private String normalizeCategoryForUpdate(String category) {
        String normalized = normalizeCategory(category);
        return normalized == null ? "" : normalized;
    }

    /**
     * 读取文件已有正文。
     *
     * @param id 文件 ID
     * @return 已有正文；文件不存在时返回 null
     * 策略：仅在重新生成摘要且本次请求未带正文时使用
     */
    private String loadExistingContent(Long id) {
        FileDO file = fileMapper.selectContentById(id);
        return file == null ? null : file.getContent();
    }

    /**
     * 规范化文章摘要。
     *
     * @param summary 原始摘要，可为 null
     * @return 规范化后的摘要；为空时返回 null，表示需要自动生成
     * 策略：去首尾空白，超过 500 个字符返回 400；空字符串按未填写处理
     */
    private String normalizeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String value = summary.trim();
        if (value.length() > MAX_SUMMARY_LENGTH) {
            throw new BusinessException(400, "文章摘要不能超过500个字符");
        }
        return value;
    }

    /**
     * 根据 Markdown 正文生成摘要。
     *
     * @param content Markdown 正文，可为 null
     * @return 摘要文本；正文为空时返回 null
     * 策略：剥离 Markdown 标记后截取前 500 个字符，作为列表页的兜底摘要
     */
    private String generateSummary(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String text = content;
        if (text.length() > SUMMARY_SCAN_LENGTH) {
            text = text.substring(0, SUMMARY_SCAN_LENGTH);
        }
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("(?m)^\\s{0,3}([-*+])\\s+", "")
                .replaceAll("(?m)^\\s{0,3}\\d+\\.\\s+", "")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[`*_~]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH) : text;
    }

    /**
     * 校验 Markdown 正文大小。
     *
     * @param content 正文
     * 策略：按 UTF-8 字节数限制为 10MB，避免单文件过大
     */
    private void validateContent(String content) {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(400, "文件内容不能超过10MB");
        }
    }

    /**
     * 规范化可见性。
     *
     * @param visibility   原始可见性
     * @param defaultValue 为空时的默认值
     * @return 规范化后的可见性
     * 策略：转大写并校验仅允许 PUBLIC / PRIVATE
     */
    private String normalizeVisibility(String visibility, String defaultValue) {
        if (visibility == null || visibility.isBlank()) {
            if (defaultValue == null) {
                throw new BusinessException(400, "可见性不能为空");
            }
            return defaultValue;
        }
        String value = visibility.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_VISIBILITIES.contains(value)) {
            throw new BusinessException(400, "可见性必须为 PUBLIC 或 PRIVATE");
        }
        return value;
    }

    /**
     * 规范化文件状态。
     *
     * @param status       原始状态
     * @param defaultValue 为空时的默认值
     * @return 规范化后的状态
     * 策略：转大写并校验仅允许 DRAFT / PENDING / PUBLISHED / REJECTED
     */
    private String normalizeStatus(String status, String defaultValue) {
        if (status == null || status.isBlank()) {
            if (defaultValue == null) {
                throw new BusinessException(400, "文件状态不能为空");
            }
            return defaultValue;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(value)) {
            throw new BusinessException(400, "文件状态必须为 DRAFT、PENDING、PUBLISHED 或 REJECTED");
        }
        return value;
    }

    /**
     * 生成内部 Markdown 正文访问地址。
     *
     * @param id 文件 ID
     * @return 正文接口地址
     * 策略：统一使用 /api/files/{id}/content，由鉴权拦截器和所有者校验保护
     */
    private String buildContentUrl(Long id) {
        return "/api/files/" + id + "/content";
    }

    /**
     * 将节点列表组装为文件树。
     *
     * @param nodes 当前用户全部节点
     * @return 根节点列表
     * 策略：先按 id 建 VO 映射，再按 parent_id 挂到父节点；父节点缺失时按根节点处理
     */
    private List<FileTreeNodeVO> buildTree(List<FileDO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, FileTreeNodeVO> voById = new HashMap<>();
        for (FileDO node : nodes) {
            voById.put(node.getId(), toTreeNode(node));
        }

        List<FileTreeNodeVO> roots = new ArrayList<>();
        for (FileDO node : nodes) {
            FileTreeNodeVO vo = voById.get(node.getId());
            FileTreeNodeVO parent = node.getParentId() == null ? null : voById.get(node.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }

        for (FileTreeNodeVO root : roots) {
            fillPath(root, "");
        }
        sortTree(roots);
        return roots;
    }

    /**
     * 将单个持久化节点转换为树节点。
     *
     * @param node 文件或文件夹记录
     * @return 树节点展示对象
     * 策略：node_type 转为前端使用的 file / folder；文件大小为空时按 0 处理
     */
    private FileTreeNodeVO toTreeNode(FileDO node) {
        FileTreeNodeVO vo = new FileTreeNodeVO();
        vo.setId(node.getId());
        vo.setParentId(node.getParentId());
        vo.setName(node.getTitle());
        vo.setType(NODE_TYPE_FOLDER.equals(node.getNodeType()) ? "folder" : "file");
        vo.setSize(node.getFileSize() == null ? 0L : node.getFileSize());
        vo.setUpdatedAt(node.getUpdatedAt());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    /**
     * 递归填充树节点路径。
     *
     * @param node       当前节点
     * @param parentPath 父节点路径；根节点传空字符串
     * 策略：路径使用 / 连接各级 title
     */
    private void fillPath(FileTreeNodeVO node, String parentPath) {
        node.setPath(parentPath.isEmpty() ? node.getName() : parentPath + "/" + node.getName());
        for (FileTreeNodeVO child : node.getChildren()) {
            fillPath(child, node.getPath());
        }
    }

    /**
     * 递归排序文件树。
     *
     * @param nodes 待排序节点
     * 策略：文件夹优先，其次按名称忽略大小写升序，最后按 id 升序
     */
    private void sortTree(List<FileTreeNodeVO> nodes) {
        nodes.sort(Comparator
                .comparing((FileTreeNodeVO node) -> !"folder".equals(node.getType()))
                .thenComparing(node -> node.getName() == null ? "" : node.getName().toLowerCase(Locale.ROOT))
                .thenComparing(FileTreeNodeVO::getId));
        for (FileTreeNodeVO node : nodes) {
            sortTree(node.getChildren());
        }
    }

    /**
     * 查询节点并校验归属。
     *
     * @param id     节点 ID
     * @param userId 当前用户 ID
     * @return 节点记录
     * 策略：节点不存在或不属于当前用户时统一返回 404，避免泄露他人节点
     */
    private FileDO requireOwnedNode(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(400, "节点 ID 不能为空");
        }
        FileDO node = fileMapper.selectById(id);
        if (node == null || !userId.equals(node.getUserId())) {
            throw new BusinessException(404, "文件或文件夹不存在");
        }
        return node;
    }

    /**
     * 校验并返回父文件夹 ID。
     *
     * @param userId   当前用户 ID
     * @param parentId 父节点 ID，可为 null
     * @return 规范化后的父节点 ID
     * 策略：null 表示根目录；非 null 时要求节点存在、属于当前用户且类型为 FOLDER
     */
    private Long resolveParentId(Long userId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        FileDO parent = fileMapper.selectById(parentId);
        if (parent == null || !userId.equals(parent.getUserId())) {
            throw new BusinessException(404, "父文件夹不存在");
        }
        if (!NODE_TYPE_FOLDER.equals(parent.getNodeType())) {
            throw new BusinessException(400, "目标节点不是文件夹");
        }
        return parentId;
    }

    /**
     * 校验同級节点名称是否可用。
     *
     * @param userId    当前用户 ID
     * @param parentId  父节点 ID，可为 null
     * @param name      节点名称
     * @param excludeId 需要排除的节点 ID，可为 null
     * 策略：同级存在同名节点时返回 409；重命名时排除自身
     */
    private void assertSiblingNameAvailable(Long userId, Long parentId, String name, Long excludeId) {
        if (fileMapper.countSiblingByTitle(userId, parentId, name, excludeId) > 0) {
            throw new BusinessException(409, "同级目录下已存在同名文件或文件夹");
        }
    }

    /**
     * 校验并规范化节点名称。
     *
     * @param name 原始名称
     * @return 去除首尾空白后的名称
     * 策略：名称不能为空、不超过 255 字符，且不能包含 / 或 \ 等路径分隔符
     */
    private String normalizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "名称不能为空");
        }
        String value = name.trim();
        if (value.length() > MAX_NODE_NAME_LENGTH) {
            throw new BusinessException(400, "名称不能超过255个字符");
        }
        if (".".equals(value) || "..".equals(value)
                || value.contains("/") || value.contains("\\")) {
            throw new BusinessException(400, "名称不能包含路径分隔符");
        }
        return value;
    }

    /**
     * 查询单个节点的树展示对象并计算完整路径。
     *
     * @param userId 当前用户 ID
     * @param node   节点记录
     * @return 带 path 的树节点
     * 策略：读取当前用户全部节点构建父链，避免为每个节点单独查库
     */
    private FileTreeNodeVO toTreeNodeWithPath(Long userId, FileDO node) {
        Map<Long, FileDO> byId = loadNodeMap(userId);
        FileTreeNodeVO vo = toTreeNode(node);
        vo.setPath(buildPath(node, byId));
        return vo;
    }

    /**
     * 读取当前用户全部节点并建立 id 映射。
     *
     * @param userId 当前用户 ID
     * @return id 到节点的映射
     * 策略：单次查询后在内存中建立 HashMap
     */
    private Map<Long, FileDO> loadNodeMap(Long userId) {
        Map<Long, FileDO> byId = new HashMap<>();
        for (FileDO item : fileMapper.selectAllByUserId(userId)) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    /**
     * 根据父链计算节点路径。
     *
     * @param node 节点记录
     * @param byId 当前用户节点映射
     * @return 以 / 分隔的路径
     * 策略：向上遍历 parent_id 并反转结果，使用 seen 集合防止脏数据形成死循环
     */
    private String buildPath(FileDO node, Map<Long, FileDO> byId) {
        List<String> parts = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        FileDO cursor = node;
        while (cursor != null && seen.add(cursor.getId())) {
            parts.add(cursor.getTitle());
            cursor = cursor.getParentId() == null ? null : byId.get(cursor.getParentId());
        }
        Collections.reverse(parts);
        return String.join("/", parts);
    }

    /**
     * 判断 candidateId 是否为 ancestorId 的后代。
     *
     * @param userId      当前用户 ID
     * @param candidateId 待检查节点 ID
     * @param ancestorId  祖先节点 ID
     * @return 是否为后代
     * 策略：从 candidateId 沿 parent_id 向上遍历，命中 ancestorId 即返回 true
     */
    private boolean isDescendantOf(Long userId, Long candidateId, Long ancestorId) {
        Map<Long, FileDO> byId = loadNodeMap(userId);
        Set<Long> seen = new HashSet<>();
        FileDO cursor = byId.get(candidateId);
        while (cursor != null && seen.add(cursor.getId())) {
            if (ancestorId.equals(cursor.getId())) {
                return true;
            }
            cursor = cursor.getParentId() == null ? null : byId.get(cursor.getParentId());
        }
        return false;
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
     * 将文件持久化对象转换为详情展示对象。
     *
     * @param file 文件持久化对象
     * @return 文件详情展示对象
     * 策略：逐字段复制并解析图片 JSON，不返回 md_object_key 和正文
     */
    private FileDetailVO toDetailVO(FileDO file) {
        FileDetailVO vo = new FileDetailVO();
        vo.setId(file.getId());
        vo.setUserId(file.getUserId());
        vo.setTitle(file.getTitle());
        vo.setOriginalName(file.getOriginalName());
        vo.setMdUrl(file.getMdUrl());
        vo.setStorageProvider(file.getStorageProvider());
        vo.setImages(parseImages(file.getImgUrls()));
        vo.setFileSize(file.getFileSize());
        vo.setVisibility(file.getVisibility());
        vo.setStatus(file.getStatus());
        vo.setCategory(file.getCategory());
        vo.setSummary(file.getSummary());
        vo.setAuthorNickname(file.getAuthorNickname());
        vo.setAuthorUsername(file.getAuthorUsername());
        vo.setCreatedAt(file.getCreatedAt());
        vo.setUpdatedAt(file.getUpdatedAt());
        return vo;
    }

    /**
     * 将文件持久化对象转换为列表展示对象。
     *
     * @param file 文件持久化对象
     * @return 文件列表展示对象
     * 策略：逐字段复制，解析图片 JSON，并取第一张图片的 URL 作为封面
     */
    private FileVO toVO(FileDO file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setUserId(file.getUserId());
        vo.setTitle(file.getTitle());
        vo.setOriginalName(file.getOriginalName());
        vo.setMdUrl(file.getMdUrl());
        vo.setStorageProvider(file.getStorageProvider());
        List<ImageInfoVO> images = parseImages(file.getImgUrls());
        vo.setCoverUrl(images.isEmpty() ? null : images.get(0).getUrl());
        vo.setFileSize(file.getFileSize());
        vo.setVisibility(file.getVisibility());
        vo.setStatus(file.getStatus());
        vo.setCategory(file.getCategory());
        vo.setSummary(file.getSummary());
        vo.setAuthorNickname(file.getAuthorNickname());
        vo.setAuthorUsername(file.getAuthorUsername());
        vo.setCreatedAt(file.getCreatedAt());
        vo.setUpdatedAt(file.getUpdatedAt());
        return vo;
    }

    /**
     * 将文件持久化对象转换为管理员元数据展示对象。
     *
     * @param file 文件持久化对象
     * @return 管理员文件元数据
     * 策略：不返回 mdUrl、md_object_key 和正文，避免管理端接触用户私有资源地址
     */
    private AdminFileVO toAdminVO(FileDO file) {
        AdminFileVO vo = new AdminFileVO();
        vo.setId(file.getId());
        vo.setUserId(file.getUserId());
        vo.setTitle(file.getTitle());
        vo.setOriginalName(file.getOriginalName());
        vo.setStorageProvider(file.getStorageProvider());
        List<ImageInfoVO> images = parseImages(file.getImgUrls());
        vo.setCoverUrl(images.isEmpty() ? null : images.get(0).getUrl());
        vo.setFileSize(file.getFileSize());
        vo.setVisibility(file.getVisibility());
        vo.setStatus(file.getStatus());
        vo.setCategory(file.getCategory());
        vo.setAuthorNickname(file.getAuthorNickname());
        vo.setCreatedAt(file.getCreatedAt());
        vo.setUpdatedAt(file.getUpdatedAt());
        return vo;
    }

    /**
     * 解析文件图片 JSON 字符串。
     *
     * @param imgUrls 图片对象数组 JSON 字符串，可为空
     * @return 图片信息列表；输入为空时返回空列表
     * 策略：空白值直接返回空列表，否则使用 Jackson 反序列化为 List<ImageInfoVO>，格式错误时抛出 500 业务异常
     */
    private List<ImageInfoVO> parseImages(String imgUrls) {
        if (imgUrls == null || imgUrls.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imgUrls, IMAGE_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "图片数据格式错误");
        }
    }
}

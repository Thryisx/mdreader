package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件树节点展示对象。
 */
@Getter
@Setter
public class FileTreeNodeVO {

    /**
     * 节点 ID
     */
    private Long id;

    /**
     * 父节点 ID；NULL 表示位于根目录
     */
    private Long parentId;

    /**
     * 节点名称（文件含扩展名）
     */
    private String name;

    /**
     * 节点类型：file / folder
     */
    private String type;

    /**
     * 从根目录到当前节点的路径，使用 / 分隔
     */
    private String path;

    /**
     * 文件大小（字节）；文件夹为 0
     */
    private Long size;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 子节点列表；文件节点返回空列表
     */
    private List<FileTreeNodeVO> children = new ArrayList<>();
}

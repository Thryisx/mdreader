package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建文件夹请求参数。
 */
@Getter
@Setter
public class CreateFolderDTO {

    /**
     * 文件夹名称
     */
    private String name;

    /**
     * 父文件夹 ID；为空时创建在根目录
     */
    private Long parentId;
}

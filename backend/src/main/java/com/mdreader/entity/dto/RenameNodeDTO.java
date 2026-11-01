package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 重命名文件或文件夹请求参数。
 */
@Getter
@Setter
public class RenameNodeDTO {

    /**
     * 新名称；文件建议保留扩展名
     */
    private String name;
}

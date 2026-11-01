package com.mdreader.entity.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 移动文件或文件夹请求参数。
 */
@Getter
@Setter
public class MoveNodeDTO {

    /**
     * 目标父文件夹 ID；为空表示移动到根目录
     */
    private Long parentId;
}

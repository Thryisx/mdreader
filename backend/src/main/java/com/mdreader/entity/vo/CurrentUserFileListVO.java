package com.mdreader.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 当前用户文件列表分页数据。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserFileListVO {

    /**
     * 当前用户的文件总数
     */
    private Long total;

    /**
     * 当前页文件列表
     */
    private List<FileListItemVO> records;
}

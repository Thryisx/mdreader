package com.mdreader.entity.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * Markdown 正文读取结果。
 */
@Getter
@Setter
public class FileContentVO {

    /**
     * Markdown 正文；为空表示正文由外部 md_url 提供
     */
    private String content;

    /**
     * 外部 Markdown URL；内部存储时为空
     */
    private String mdUrl;
}

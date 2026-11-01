package com.mdreader.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 分页查询结果。
 *
 * @param <T> 当前页数据类型
 */
@Getter
@Setter
@NoArgsConstructor
public class PageResult<T> {

    /**
     * 当前页数据
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private long pageNum;

    /**
     * 每页条数
     */
    private long pageSize;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 构造分页结果。
     *
     * @param records  当前页数据
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * 策略：根据总记录数和每页条数向上取整计算总页数，pageSize 小于等于 0 时总页数为 0
     */
    public PageResult(List<T> records, long total, long pageNum, long pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
    }
}

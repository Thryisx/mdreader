package com.mdreader.utils;

import com.mdreader.common.constant.SystemConstants;

public final class PageUtil {

    private PageUtil() {
    }

    public static long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? SystemConstants.DEFAULT_PAGE_NUM : pageNum;
    }

    public static long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return SystemConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, SystemConstants.MAX_PAGE_SIZE);
    }

    public static long calculateOffset(long pageNum, long pageSize) {
        return (pageNum - 1) * pageSize;
    }
}

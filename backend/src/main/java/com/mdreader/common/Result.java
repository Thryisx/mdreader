package com.mdreader.common;

import com.mdreader.common.constant.SystemConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统一响应结果。
 *
 * @param <T> 业务数据类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 业务状态码
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @return 成功响应
     * 策略：使用统一成功状态码和消息
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SystemConstants.SUCCESS_CODE, SystemConstants.SUCCESS_MSG, data);
    }

    /**
     * 构造失败响应。
     *
     * @param code    业务状态码
     * @param message 提示信息
     * @return 失败响应
     * 策略：失败响应不携带业务数据，data 固定为 null
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}

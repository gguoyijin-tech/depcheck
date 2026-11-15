package com.company.depcheck.common.result;

/**
 * 统一响应体。
 *
 * @param <T> 业务数据类型
 * @author depcheck
 * @since 1.0.0
 */
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /**
     * 无参构造器（供反序列化使用）。
     */
    public Result() {
    }

    /**
     * 全参构造器。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    业务数据
     */
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应（有数据）。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 统一响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（无数据），用于删除、取消等接口。
     *
     * @return 统一响应，data 为 null
     */
    public static Result<Void> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应（旧名，等价于 {@link #ok(Object)}）。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 统一响应
     * @deprecated 请使用 {@link #ok(Object)} 替代
     */
    @Deprecated
    public static <T> Result<T> success(T data) {
        return ok(data);
    }

    /**
     * 失败响应。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 统一响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

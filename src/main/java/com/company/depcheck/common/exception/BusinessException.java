package com.company.depcheck.common.exception;

import com.company.depcheck.common.result.ResultCode;

/**
 * 业务异常：带错误码，由全局异常处理器统一转成 Result。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 按错误码构造业务异常。
     *
     * @param resultCode 预定义错误码
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 按错误码与原始异常构造业务异常。
     *
     * @param resultCode 预定义错误码
     * @param cause      原始异常
     */
    public BusinessException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.code = resultCode.getCode();
    }

    /**
     * 按自定义错误码与提示信息构造业务异常。
     *
     * @param code    错误码
     * @param message 错误提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 按自定义错误码、提示信息与原始异常构造业务异常。
     *
     * @param code    错误码
     * @param message 错误提示
     * @param cause   原始异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

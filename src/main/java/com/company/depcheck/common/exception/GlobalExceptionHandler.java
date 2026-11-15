package com.company.depcheck.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.company.depcheck.common.result.Result;
import com.company.depcheck.common.result.ResultCode;

/**
 * 全局异常处理：任何异常都要变成"有错误码的 JSON"，禁止把堆栈抛给前端。
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        LOG.warn("业务异常 code={} message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理参数校验异常。
     *
     * @param ex 参数校验异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String msg = fieldError == null ? ResultCode.PARAM_INVALID.getMessage() : fieldError.getDefaultMessage();
        LOG.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 处理上传文件超限异常。
     *
     * @param ex 上传大小超限异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        LOG.warn("上传文件超限: {}", ex.getMessage());
        return Result.fail(ResultCode.FILE_TOO_LARGE.getCode(), ResultCode.FILE_TOO_LARGE.getMessage());
    }

    /**
     * 兜底处理未预期异常。
     *
     * @param ex 未知异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception ex) {
        LOG.error("未捕获异常", ex);
        return Result.fail(ResultCode.UNKNOWN.getCode(), ResultCode.UNKNOWN.getMessage());
    }
}

package com.company.depcheck.common.result;

/**
 * 业务错误码。
 *
 * <p>区间约定：0 成功；1xxx 通用；2xxx 上传；3xxx 扫描；4xxx 报告。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
public enum ResultCode {

    /** 成功。 */
    SUCCESS(0, "success"),

    /** 参数不合法。 */
    PARAM_INVALID(1001, "参数不合法"),

    /** 未登录或会话已过期。 */
    UNAUTHORIZED(1002, "未登录或会话已过期"),

    /** 资源不存在。 */
    NOT_FOUND(1003, "资源不存在"),

    /** 系统繁忙（队列满、线程池拒绝）。 */
    SERVER_BUSY(1004, "系统繁忙，请稍后重试"),

    /** 上传文件超过大小上限。 */
    FILE_TOO_LARGE(2001, "文件超过 512MB 上限"),

    /** 不是合法 ZIP（魔数不对 / 后缀不对 / 解压失败）。 */
    INVALID_ZIP(2002, "不是合法的 ZIP 文件"),

    /** 解压后总体积或文件数超限（疑似 ZIP 炸弹）。 */
    ZIP_LIMIT_EXCEEDED(2003, "压缩包解压后过大或文件数过多"),

    /** 扫描失败（引擎异常 / 报告生成失败）。 */
    SCAN_FAILED(3001, "扫描失败"),

    /** 未知异常。 */
    UNKNOWN(1099, "系统异常");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

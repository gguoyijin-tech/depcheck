package com.company.depcheck.common.util;

import org.springframework.util.StringUtils;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;

/**
 * 会话标识工具：统一"空值兜底 + 长度校验"，避免每个 Service 各写一份。
 *
 * @author depcheck
 * @since 1.0.0
 */
public final class SessionUtils {

    /** 默认会话标识，与 project.owner_session、前端默认值保持一致。 */
    public static final String DEFAULT_SESSION = "default";

    /** 与 project.owner_session 列长度一致。 */
    public static final int MAX_SESSION_LEN = 64;

    private SessionUtils() {
    }

    /**
     * 归一化会话标识：空白取默认值，超长直接拒绝（防超长写入）。
     *
     * @param sessionId 原始会话标识，可为 null
     * @return 归一化后的会话标识
     * @throws BusinessException 超长时抛 1001
     */
    public static String normalize(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return DEFAULT_SESSION;
        }
        String trimmed = sessionId.trim();
        if (trimmed.length() > MAX_SESSION_LEN) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "会话标识过长（上限 " + MAX_SESSION_LEN + "）");
        }
        return trimmed;
    }
}

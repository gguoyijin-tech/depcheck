package com.company.depcheck.common.log;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成 traceId，写入 MDC 与响应头，日志里用 %X{traceId} 输出。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求头/响应头名称。 */
    public static final String TRACE_ID = "X-Trace-Id";

    /** MDC 中的键名。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** 生成的 traceId 长度。 */
    private static final int TRACE_ID_LEN = 16;

    /** 客户端传入的 traceId 最大长度（仅允许字母、数字、短横线，防日志注入）。 */
    private static final int MAX_HEADER_LEN = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID);
        if (!isValidTraceId(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LEN);
        }
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /**
     * 校验外部传入的 traceId 是否合法，防止换行符等日志伪造注入。
     */
    private boolean isValidTraceId(String traceId) {
        if (!StringUtils.hasText(traceId) || traceId.length() > MAX_HEADER_LEN) {
            return false;
        }
        for (int i = 0; i < traceId.length(); i++) {
            char ch = traceId.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}

package com.company.depcheck.common.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.company.depcheck.common.result.Result;
import com.company.depcheck.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 鉴权过滤器：除白名单外，所有 {@code /api/**} 必须带有效 token。
 *
 * <p>规则（对应计划"接口必须鉴权"）：</p>
 * <ol>
 *   <li>白名单：{@code /api/ping}、{@code /api/auth/login}、预检请求；</li>
 *   <li>其余请求必须有 {@code Authorization: Bearer <token>}，否则 401 + code 1002；</li>
 *   <li>{@code X-Session-Id} 必须与 token 绑定的会话一致，防止拿别人的会话标识越权；</li>
 *   <li>通过后把用户名放进请求属性，便于后续审计。</li>
 * </ol>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

    /** 免鉴权路径。 */
    private static final Set<String> WHITELIST = Set.of("/api/ping", "/api/auth/login");

    /** 请求属性：当前登录账号。 */
    public static final String ATTR_USERNAME = "authUsername";

    /** Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 会话标识请求头。 */
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    private final TokenStore tokenStore;

    private final ObjectMapper objectMapper;

    /**
     * 构造注入。
     *
     * @param tokenStore   token 存储
     * @param objectMapper JSON 序列化（复用 Spring 的）
     */
    public AuthFilter(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || WHITELIST.contains(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        TokenStore.TokenInfo info = tokenStore.validate(token);
        if (info == null) {
            LOG.warn("未登录访问 path={} token={}", path, token == null ? "无" : "无效");
            writeUnauthorized(response, ResultCode.UNAUTHORIZED.getMessage());
            return;
        }

        // 退出/查询自身会话只需要 token，不要求带业务会话头
        if (path.startsWith("/api/auth/")) {
            request.setAttribute(ATTR_USERNAME, info.username());
            filterChain.doFilter(request, response);
            return;
        }

        String sessionId = request.getHeader(HEADER_SESSION_ID);
        if (sessionId == null || !sessionId.equals(info.sessionId())) {
            LOG.warn("会话标识与会话 token 不匹配 path={} user={}", path, info.username());
            writeUnauthorized(response, "会话标识无效，请重新登录");
            return;
        }

        request.setAttribute(ATTR_USERNAME, info.username());
        filterChain.doFilter(request, response);
    }

    /** 从 Authorization 头取 token。 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    /** 输出统一结构的 401（前端拦截器据此跳登录页）。 */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

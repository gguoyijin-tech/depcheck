package com.company.depcheck.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.depcheck.common.auth.TokenStore;
import com.company.depcheck.common.result.Result;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.config.AuthProperties;
import com.company.depcheck.model.dto.LoginRequest;
import com.company.depcheck.model.vo.LoginVO;

/**
 * 登录接口：固定账号（计划 5.9）。
 *
 * <p>几个约定：</p>
 * <ul>
 *   <li>账号或密码错都返回同一句话"账号或密码错误"，不暴露账号是否存在；</li>
 *   <li>同一 IP 连续失败 5 次锁定 1 分钟（简单防暴力破解，计划 5.9）；</li>
 *   <li>登录成功签发 token + 独立 sessionId，业务数据按 sessionId 隔离（计划 R4）。</li>
 * </ul>
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    /** 允许的连续失败次数。 */
    private static final int MAX_FAILURES = 5;

    /** 失败计数窗口（秒）。 */
    private static final long WINDOW_SECONDS = 60L;

    /** 统一错误提示（不区分账号/密码）。 */
    private static final String BAD_CREDENTIALS = "账号或密码错误";

    private final AuthProperties authProperties;

    private final TokenStore tokenStore;

    /** 简易失败计数：IP → 窗口内失败次数与窗口起点。 */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 构造注入。
     *
     * @param authProperties 登录配置
     * @param tokenStore     token 存储
     */
    public AuthController(AuthProperties authProperties, TokenStore tokenStore) {
        this.authProperties = authProperties;
        this.tokenStore = tokenStore;
    }

    /**
     * 登录。
     *
     * @param request 账号密码
     * @param servletRequest 用于取客户端 IP（限流）
     * @return token 与 sessionId
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String client = clientKey(servletRequest);
        if (isLocked(client)) {
            LOG.warn("登录失败次数过多，暂时锁定 client={}", client);
            return Result.fail(ResultCode.SERVER_BUSY.getCode(), "尝试过于频繁，请 1 分钟后再试");
        }

        if (!matches(authProperties.getUsername(), request.getUsername())
                || !matches(authProperties.getPassword(), request.getPassword())) {
            recordFailure(client);
            LOG.warn("登录失败 client={} username={}", client, request.getUsername());
            return Result.fail(ResultCode.UNAUTHORIZED.getCode(), BAD_CREDENTIALS);
        }

        attempts.remove(client);
        TokenStore.TokenInfo info = tokenStore.issue(authProperties.getUsername());
        LoginVO vo = new LoginVO();
        vo.setToken(info.token());
        vo.setUsername(info.username());
        vo.setSessionId(info.sessionId());
        vo.setExpiresAt(info.expiresAt());
        return Result.ok(vo);
    }

    /**
     * 退出登录（使当前 token 立即失效）。
     *
     * @param authorization Authorization 请求头
     * @return 空成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenStore.revoke(authorization.substring("Bearer ".length()).trim());
        }
        return Result.ok();
    }

    /**
     * 当前会话信息（前端刷新页面后校验 token 是否还有效）。
     *
     * @param authorization Authorization 请求头
     * @return 账号与 sessionId
     */
    @GetMapping("/me")
    public Result<LoginVO> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization == null ? null : authorization.substring("Bearer ".length()).trim();
        TokenStore.TokenInfo info = tokenStore.validate(token);
        if (info == null) {
            return Result.fail(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
        }
        LoginVO vo = new LoginVO();
        vo.setToken(info.token());
        vo.setUsername(info.username());
        vo.setSessionId(info.sessionId());
        vo.setExpiresAt(info.expiresAt());
        return Result.ok(vo);
    }

    /** 定长比较，避免按字符短路带来的时序差异。 */
    private boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 取客户端 IP（反向代理场景下取 X-Forwarded-For 第一段）。 */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 是否处于锁定期。 */
    private boolean isLocked(String client) {
        Attempt attempt = attempts.get(client);
        if (attempt == null) {
            return false;
        }
        if (attempt.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(LocalDateTime.now())) {
            attempts.remove(client);
            return false;
        }
        return attempt.count >= MAX_FAILURES;
    }

    /** 记一次失败。 */
    private void recordFailure(String client) {
        LocalDateTime now = LocalDateTime.now();
        attempts.compute(client, (key, old) -> {
            if (old == null || old.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Attempt(1, now);
            }
            return new Attempt(old.count + 1, old.windowStart);
        });
    }

    /** 失败计数窗口。 */
    private record Attempt(int count, LocalDateTime windowStart) {
    }
}

package com.company.depcheck.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.company.depcheck.config.AuthProperties;

/**
 * 会话 token 存储（内存）。
 *
 * <p>为什么不用 JWT：一期是单实例、单账号，用不透明 token + 内存表更简单，
 * 还能"服务端主动踢人"（重启即全部失效）。多实例部署时再换 Redis 或 JWT。</p>
 *
 * <p><b>数据隔离按账号</b>（PRD 4.4.3「报告仅对提交任务的用户可见」）：
 * 同一个账号在任何浏览器登录，都落到同一个会话标识，因此能看到自己提交的全部任务；
 * token 只是"证明你是谁"，不参与数据归属。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class TokenStore {

    private static final Logger LOG = LoggerFactory.getLogger(TokenStore.class);

    /** token 长度（去掉短横线后的 UUID）。 */
    private static final int TOKEN_LEN = 32;

    /** 账号会话标识前缀。 */
    private static final String USER_SESSION_PREFIX = "user-";

    /** 摘要取前多少字节拼成 16 位十六进制。 */
    private static final int DIGEST_BYTES = 8;

    private final AuthProperties authProperties;

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    /**
     * 构造注入。
     *
     * @param authProperties 登录配置（token 有效期）
     */
    public TokenStore(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 签发 token。
     *
     * @param username 账号
     * @return token 信息（会话标识由账号稳定推导）
     */
    public TokenInfo issue(String username) {
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        if (token.length() > TOKEN_LEN) {
            token = token.substring(0, TOKEN_LEN);
        }
        String sessionId = sessionIdOf(username);
        TokenInfo info = new TokenInfo(token, username, sessionId,
                LocalDateTime.now().plusHours(Math.max(authProperties.getTokenTtlHours(), 1)));
        tokens.put(token, info);
        LOG.info("签发会话 token user={} sessionId={} 有效期至 {}", username, sessionId, info.expiresAt());
        return info;
    }

    /**
     * 校验 token。
     *
     * @param token 请求带来的 token
     * @return 有效则返回信息，无效/过期返回 null
     */
    public TokenInfo validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return null;
        }
        if (info.expiresAt().isBefore(LocalDateTime.now())) {
            tokens.remove(token);
            LOG.info("会话 token 已过期 user={} sessionId={}", info.username(), info.sessionId());
            return null;
        }
        return info;
    }

    /**
     * 注销 token。
     *
     * @param token 请求带来的 token
     */
    public void revoke(String token) {
        if (token == null) {
            return;
        }
        TokenInfo removed = tokens.remove(token);
        if (removed != null) {
            LOG.info("注销会话 token user={} sessionId={}", removed.username(), removed.sessionId());
        }
    }

    /**
     * 账号 → 会话标识（确定性映射）。
     *
     * <p>同一账号永远得到同一个标识，换浏览器/重新登录都能看到自己的历史任务。</p>
     *
     * @param username 账号
     * @return 形如 {@code user-8f7ad16d42fbfcab}
     */
    public String sessionIdOf(String username) {
        String name = username == null ? "" : username;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(DIGEST_BYTES * 2);
            for (int i = 0; i < DIGEST_BYTES; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return USER_SESSION_PREFIX + hex;
        } catch (NoSuchAlgorithmException ex) {
            LOG.warn("SHA-256 不可用，退化为 hashCode 映射 user={}", name, ex);
            return USER_SESSION_PREFIX + Integer.toHexString(name.hashCode());
        }
    }

    /** 清理过期 token（每次签发时顺带做一次，量级很小）。 */
    private void purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        tokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    /**
     * 一次登录的会话信息。
     *
     * @param token     访问 token
     * @param username  账号
     * @param sessionId 数据隔离用的会话标识（账号级）
     * @param expiresAt 过期时间
     */
    public record TokenInfo(String token, String username, String sessionId, LocalDateTime expiresAt) {
    }
}

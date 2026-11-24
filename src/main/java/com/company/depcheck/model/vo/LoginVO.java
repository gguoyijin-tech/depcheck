package com.company.depcheck.model.vo;

import java.time.LocalDateTime;

/**
 * 登录结果：token + 该次登录专属的会话标识。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class LoginVO {

    private String token;
    private String username;
    private String sessionId;
    private LocalDateTime expiresAt;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}

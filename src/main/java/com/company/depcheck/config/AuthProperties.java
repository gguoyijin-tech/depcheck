package com.company.depcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录配置，对应 application.yaml 的 depcheck.auth.*。
 *
 * <p>一期按计划 5.9 用固定账号（单账号），登录成功后签发会话 token；
 * 同账号登录两次 = 两个会话，数据互不可见（计划 R4 的会话级隔离）。</p>
 *
 * <p><b>上线前必须改掉默认密码</b>，或改用环境变量注入。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "depcheck.auth")
public class AuthProperties {

    /** 固定账号。 */
    private String username = "depcheck";

    /** 固定密码。 */
    private String password = "Depcheck@2026";

    /** token 有效期（小时）。 */
    private int tokenTtlHours = 12;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getTokenTtlHours() {
        return tokenTtlHours;
    }

    public void setTokenTtlHours(int tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours;
    }
}

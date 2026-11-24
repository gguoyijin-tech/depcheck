package com.company.depcheck.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class LoginRequest {

    /** 账号。 */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 密码。 */
    @NotBlank(message = "密码不能为空")
    private String password;


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

}
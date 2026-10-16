package com.gudu.xsd.modules.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录入参。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录场景：admin = 管理后台（仅平台超管可登录）；空 = C 端。 */
    private String scope;

    public LoginDTO(String username, String password) {
        this(username, password, null);
    }
}

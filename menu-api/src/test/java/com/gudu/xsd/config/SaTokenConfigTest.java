package com.gudu.xsd.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 登录拦截规则：默认需登录；公共路径免登录；公共只读接口仅 GET（及菜谱 POST 搜索）免登录。 */
class SaTokenConfigTest {

    @ParameterizedTest(name = "{0} {1} → requiresLogin={2}")
    @CsvSource({
            // 家庭数据：一律需登录
            "GET,    /menu/page,                true",
            "DELETE, /menu/10,                  true",
            "GET,    /pantry/grouped,           true",
            "POST,   /member/current,           true",
            "PUT,    /ai/provider,              true",
            "POST,   /dish/import-url,          true",
            "POST,   /dish/semantic-search,     true",
            // 公共只读：游客可 GET
            "GET,    /dish/search,              false",
            "POST,   /dish/search,              false",
            "GET,    /dish/12,                  false",
            "GET,    /dish/12/nutrition,        false",
            "GET,    /dict,                     false",
            "GET,    /ingredient/3,             false",
            // 同路径写操作仍需登录
            "POST,   /dish,                     true",
            "PUT,    /dish,                     true",
            "DELETE, /dish/12,                  true",
            "POST,   /dict,                     true",
            "POST,   /ingredient,               true",
            "GET,    /dish/12/history,          true",
            // 公共路径
            "POST,   /auth/login,               false",
            "POST,   /auth/wx-login,            false",
            "GET,    /uploads/a.png,            false",
            "GET,    /menu/5/together,          false",
            "OPTIONS,/menu/page,                false",
    })
    void rules(String method, String path, boolean expected) {
        assertThat(SaTokenConfig.requiresLogin(method.trim(), path.trim())).isEqualTo(expected);
    }
}

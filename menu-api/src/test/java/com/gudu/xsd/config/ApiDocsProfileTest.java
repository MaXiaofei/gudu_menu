package com.gudu.xsd.config;

import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.file.FileController;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A.2：prod profile 下接口文档完全关闭（404）且不在免登录名单（开启登录校验时 401）；
 * dev profile 下文档可用（证明同一套装配里 springdoc 确实在，404 不是测试装配缺失造成的）。
 */
class ApiDocsProfileTest {

    @TestConfiguration
    static class TestSqlConfig {
        @Bean
        DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) {
            return new DefaultSqlSessionFactory(new Configuration(
                    new Environment("test", new JdbcTransactionFactory(), ds)));
        }
    }

    @ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
            SpringDocWebMvcConfiguration.class,
            org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
            SwaggerUiOAuthProperties.class, cn.dev33.satoken.spring.SaTokenContextRegister.class,
            cn.dev33.satoken.spring.SaBeanRegister.class, cn.dev33.satoken.spring.SaBeanInject.class})
    @Import({TestSqlConfig.class, OpenApiConfig.class})
    abstract static class Base {
        @Autowired MockMvc mvc;
        @MockBean ContentSecurityAppService contentSecurity;
    }

    @Nested
    @WebMvcTest(value = FileController.class, properties = {
            "auth.login-check=false",
            "gudu.upload-sign.secret=api-docs-test-secret-0123456",
            "logging.file.path=target/test-logs"})
    @ActiveProfiles("prod")
    class 生产_未登录校验时也是404 extends Base {
        @Test
        void 文档接口与页面_404() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mvc.perform(get("/v3/api-docs/gudu")).andExpect(status().isNotFound());
            mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @WebMvcTest(value = FileController.class, properties = {
            "auth.login-check=true",
            "gudu.upload-sign.secret=api-docs-test-secret-0123456",
            "logging.file.path=target/test-logs"})
    @ActiveProfiles("prod")
    class 生产_开启登录校验_未登录401 extends Base {
        @Test
        void 文档路径不在放行名单() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(jsonPath("$.code").value(401));
            mvc.perform(get("/swagger-ui/index.html")).andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @WebMvcTest(value = FileController.class, properties = "auth.login-check=true")
    class 开发_文档可用且免登录 extends Base {
        @Test
        void 文档可访问() throws Exception {
            mvc.perform(get("/v3/api-docs/gudu")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.openapi").exists());
            mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}

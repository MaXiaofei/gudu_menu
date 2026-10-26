package com.gudu.xsd.modules.backoffice.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.backoffice.application.BackofficeAppService;
import com.gudu.xsd.modules.backoffice.application.dto.BackofficeViews;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.interfaces.OperatorGuardAspect;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 运营后台接口：非 OPERATOR / SUPER_ADMIN 一律 403 且不触达应用层；运营可查可封禁，封禁后下线目标账号。 */
@WebMvcTest(
        value = BackofficeController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import({BackofficeControllerTest.TestSqlConfig.class, OperatorGuardAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class BackofficeControllerTest {

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

    @Autowired MockMvc mvc;
    @MockBean BackofficeAppService app;
    @MockBean AccountQueryApi accountQuery;

    static final String REASON = "{\"reason\":\"违规\"}";

    static List<RequestBuilder> allEndpoints() {
        return List.of(
                get("/admin/accounts"),
                post("/admin/accounts/5/ban").contentType(MediaType.APPLICATION_JSON).content(REASON),
                post("/admin/accounts/5/unban").contentType(MediaType.APPLICATION_JSON).content(REASON),
                get("/admin/accounts/5/consents"),
                get("/admin/deactivations"),
                post("/admin/deactivations/1/retry"),
                get("/admin/content-violations/media"),
                get("/admin/content-violations/media/1/image"),
                post("/admin/content-violations/media/1/restore").contentType(MediaType.APPLICATION_JSON).content(REASON),
                post("/admin/content-violations/media/1/confirm").contentType(MediaType.APPLICATION_JSON).content(REASON),
                get("/admin/content-violations/text"),
                post("/admin/content-violations/text/1/confirm").contentType(MediaType.APPLICATION_JSON).content(REASON),
                get("/admin/operation-logs"));
    }

    @Test
    void 普通用户_全部后台接口403_不触达应用层() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(accountQuery.isOperator(7L)).willReturn(false);
            for (RequestBuilder rb : allEndpoints()) {
                mvc.perform(rb).andExpect(jsonPath("$.code").value(403));
            }
            verifyNoInteractions(app);
        }
    }

    @Test
    void 运营_账号列表按条件查询_只返回元数据() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(accountQuery.isOperator(1L)).willReturn(true);
            given(app.accounts(any(), anyInt(), anyInt())).willReturn(new BackofficeViews.Page<>(List.of(
                    new BackofficeViews.AccountItem(5L, "小王", "138****0001", "a1b2c3", "ACTIVE", "USER", 2, true,
                            "刷广告", LocalDateTime.of(2026, 10, 26, 9, 0), LocalDateTime.of(2026, 1, 1, 0, 0), null)),
                    1));
            mvc.perform(get("/admin/accounts").param("keyword", "小王").param("openidTail", "b2c3")
                            .param("familyId", "8").param("bannedOnly", "true"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].familyCount").value(2))
                    .andExpect(jsonPath("$.data.records[0].banned").value(true))
                    .andExpect(jsonPath("$.data.records[0].maskedPhone").value("138****0001"));
            ArgumentCaptor<AccountSearch> c = ArgumentCaptor.forClass(AccountSearch.class);
            verify(app).accounts(c.capture(), eq(1), eq(20));
            assertThat(c.getValue()).isEqualTo(new AccountSearch("小王", "b2c3", 8L, null, true));
        }
    }

    @Test
    void 运营_封禁成功后下线目标账号全部登录态() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(accountQuery.isOperator(1L)).willReturn(true);
            given(app.ban(1L, 5L, "违规")).willReturn(new AccountBanAppService.BanView(5L, "违规", 1L,
                    LocalDateTime.of(2026, 10, 26, 10, 0)));
            mvc.perform(post("/admin/accounts/5/ban").contentType(MediaType.APPLICATION_JSON).content(REASON))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.reason").value("违规"));
            stp.verify(() -> StpUtil.logout((Object) 5L));
        }
    }

    @Test
    void 运营_注销列表超期筛选透传() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(accountQuery.isOperator(1L)).willReturn(true);
            mvc.perform(get("/admin/deactivations").param("status", "FAILED").param("overdue", "true"))
                    .andExpect(jsonPath("$.code").value(0));
            verify(app).deactivations("FAILED", true, 1, 20);
        }
    }

    @Test
    void 图片类型按文件头识别() {
        assertThat(BackofficeController.imageType(new byte[]{(byte) 0xFF, (byte) 0xD8, 0, 0})).isEqualTo("image/jpeg");
        assertThat(BackofficeController.imageType(new byte[]{'G', 'I', 'F', '8'})).isEqualTo("image/gif");
        assertThat(BackofficeController.imageType(new byte[]{1, 2, 3, 4})).isEqualTo("image/png");
    }
}

package com.gudu.xsd.modules.preference.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.preference.application.PreferenceView;
import com.gudu.xsd.modules.preference.application.UserPreferenceAppService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 偏好接口：GET/PUT /preference 按登录账号。 */
@WebMvcTest(
        value = UserPreferenceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(UserPreferenceControllerTest.TestSqlConfig.class)
class UserPreferenceControllerTest {

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
    @MockBean UserPreferenceAppService appService;

    @Test
    void 读取与关闭_按登录账号() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(appService.get(7L)).willReturn(new PreferenceView(true));
            given(appService.update(7L, false)).willReturn(new PreferenceView(false));

            mvc.perform(get("/preference"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.personalizedRecommend").value(true));
            mvc.perform(put("/preference").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"personalizedRecommend\":false}"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.personalizedRecommend").value(false));
        }
    }
}

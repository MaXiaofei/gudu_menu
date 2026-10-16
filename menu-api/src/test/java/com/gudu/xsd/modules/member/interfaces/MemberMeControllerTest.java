package com.gudu.xsd.modules.member.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.member.application.MemberMeView;
import com.gudu.xsd.modules.member.application.MemberProfileAppService;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = MemberMeController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(MemberMeControllerTest.TestSqlConfig.class)
class MemberMeControllerTest {

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
    @MockBean MemberProfileAppService appService;

    @Test
    void GET_me_契约字段() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(appService.me(1L)).willReturn(new MemberMeView(1L, "张爸爸", null, "138****0001", true));
            mvc.perform(get("/member/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("张爸爸"))
                    .andExpect(jsonPath("$.data.phone").value("138****0001"))
                    .andExpect(jsonPath("$.data.isAdmin").value(true))
                    .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
        }
    }

    @Test
    void PUT_me_只改自己() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(appService.update(1L, "老张", "/gudu/uploads/a.jpg"))
                    .willReturn(new MemberMeView(1L, "老张", "/gudu/uploads/a.jpg", null, false));
            mvc.perform(put("/member/me").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":99,\"name\":\"老张\",\"avatarUrl\":\"/gudu/uploads/a.jpg\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.name").value("老张"));
            verify(appService).update(1L, "老张", "/gudu/uploads/a.jpg");
        }
    }
}

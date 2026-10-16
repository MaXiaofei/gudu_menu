package com.gudu.xsd.modules.menu.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.menu.CookMaterialsVO;
import com.gudu.xsd.modules.menu.application.CookAppService;
import com.gudu.xsd.modules.menu.application.CookDishResult;
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
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 单菜直做接口：身份取 StpUtil（MockedStatic），不信任客户端 memberId。 */
@WebMvcTest(
        value = CookDishController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(CookDishControllerTest.TestSqlConfig.class)
class CookDishControllerTest {

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
    @MockBean CookAppService appService;

    @Test
    void GET_弹窗数据_menuId为null() throws Exception {
        given(appService.dishMaterials(eq(1L), any())).willReturn(new CookMaterialsVO(null, List.of(
                new CookMaterialsVO.Item(10L, "番茄", List.of("番茄炒蛋 2个 ×2"), "LOW", false))));

        mvc.perform(get("/cook/dish/1/materials").param("servings", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].level").value("LOW"))
                .andExpect(jsonPath("$.data.items[0].isCondiment").value(false));
        verify(appService).dishMaterials(1L, new BigDecimal("2"));
    }

    @Test
    void POST_做菜_身份取登录账号() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(appService.cookDish(eq(1L), eq(7L), any(), any(), any(), any()))
                    .willReturn(new CookDishResult(55L, 1L));

            mvc.perform(post("/cook/dish/1").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"servings\":2,\"usedUp\":[10],\"partiallyUsed\":[20],\"note\":\"少盐\",\"memberId\":999}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.recordId").value(55))
                    .andExpect(jsonPath("$.data.dishId").value(1));
            verify(appService).cookDish(1L, 7L, new BigDecimal("2"), List.of(10L), List.of(20L), "少盐");
        }
    }

    @Test
    void POST_无body_按默认值() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(appService.cookDish(eq(1L), eq(7L), any(), any(), any(), any()))
                    .willReturn(new CookDishResult(56L, 1L));
            mvc.perform(post("/cook/dish/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.recordId").value(56));
            verify(appService).cookDish(eq(1L), eq(7L), isNull(), isNull(), isNull(), isNull());
        }
    }

    @Test
    void POST_备注超长_参数校验拦截() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(post("/cook/dish/1").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":\"" + "x".repeat(201) + "\"}"))
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.msg").value("备注最多 200 字"));
            verify(appService, never()).cookDish(any(), any(), any(), any(), any(), any());
        }
    }
}

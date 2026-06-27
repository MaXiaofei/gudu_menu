package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.PageQuery;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock MenuService，验证菜单端点。
 * 范式照 PantryControllerTest。
 */
@WebMvcTest(
        value = MenuController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(MenuControllerTest.TestSqlConfig.class)
class MenuControllerTest {

    @TestConfiguration
    static class TestSqlConfig {
        @Bean
        DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) {
            TransactionFactory tx = new JdbcTransactionFactory();
            Environment env = new Environment("test", tx, ds);
            Configuration cfg = new Configuration(env);
            return new DefaultSqlSessionFactory(cfg);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MenuService svc;

    private final ObjectMapper om = new ObjectMapper();

    private Menu menu(Long id, String name) {
        Menu m = new Menu();
        m.setId(id);
        m.setName(name);
        m.setServingCount(2);
        return m;
    }

    @Test
    void 菜单分页_返回200_and_分页结构() throws Exception {
        Page<Menu> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(menu(1L, "周末菜单")));
        org.mockito.Mockito.doReturn(page).when(svc).page(any(PageQuery.class));

        mvc.perform(get("/menu").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("周末菜单"));
    }

    @Test
    void 菜单详情_返回menu_and_dishes() throws Exception {
        MenuService.MenuDetail detail = new MenuService.MenuDetail(
                menu(1L, "工作日菜单"),
                List.of(new MenuDish()));
        given(svc.detail(eq(1L))).willReturn(detail);

        mvc.perform(get("/menu/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.menu.name").value("工作日菜单"))
                .andExpect(jsonPath("$.data.dishes").isArray());
    }

    @Test
    void 菜单汇总_返回总价和营养() throws Exception {
        MenuService.MenuSummary summary = new MenuService.MenuSummary(
                new BigDecimal("36.5"), Map.of(1L, new BigDecimal("200")));
        given(svc.summary(eq(1L))).willReturn(summary);

        mvc.perform(get("/menu/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalPrice").value(36.5))
                .andExpect(jsonPath("$.data.totalNutrition.1").value(200));
    }

    @Test
    void 新增菜单_返回id() throws Exception {
        MenuSaveDTO dto = new MenuSaveDTO();
        dto.setMenu(menu(99L, "新菜单"));
        dto.setDishes(List.of());
        // saveWithDishes 返回 void；save() 返回 dto.getMenu().getId()
        // 用 doAnswer 在 service 调用后断言 controller 读取的 id（这里直接桩 updateById 不影响）
        // Controller.save 返回 dto.getMenu().getId()，所以请求体里 menu.id 会被透传
        String body = "{\"menu\":{\"id\":99,\"name\":\"新菜单\",\"servingCount\":2},\"dishes\":[]}";
        mvc.perform(post("/menu").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(99));
        verify(svc).saveWithDishes(any(MenuSaveDTO.class));
    }

    @Test
    void 删除菜单_调用service() throws Exception {
        mvc.perform(delete("/menu/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).removeById(5L);
    }
}

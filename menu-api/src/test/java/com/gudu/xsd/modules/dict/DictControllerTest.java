package com.gudu.xsd.modules.dict;

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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock DictService，验证字典 CRUD 端点。
 * 范式照 PantryControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory。
 */
@WebMvcTest(
        value = DictController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(DictControllerTest.TestSqlConfig.class)
class DictControllerTest {

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
    private DictService svc;

    private final ObjectMapper om = new ObjectMapper();

    private SysDict dict(Long id, String group, String name) {
        SysDict d = new SysDict();
        d.setId(id);
        d.setDictGroup(group);
        d.setName(name);
        d.setSort(1);
        return d;
    }

    @Test
    void 字典分页_返回200_and_分页结构() throws Exception {
        Page<SysDict> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(dict(1L, "unit", "克")));
        org.mockito.Mockito.doReturn(page).when(svc).pageByGroup(eq("unit"), any(PageQuery.class));

        mvc.perform(get("/dict").param("group", "unit").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("克"));
    }

    @Test
    void 新增字典_返回id() throws Exception {
        given(svc.save(any(SysDict.class))).willAnswer(inv -> {
            ((SysDict) inv.getArgument(0)).setId(88L);
            return true;
        });

        String body = "{\"dictGroup\":\"unit\",\"name\":\"个\",\"sort\":2}";
        mvc.perform(post("/dict").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(88));
    }

    @Test
    void 更新字典_调用service() throws Exception {
        String body = "{\"id\":1,\"dictGroup\":\"unit\",\"name\":\"千克\"}";
        mvc.perform(put("/dict").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).updateById(any(SysDict.class));
    }

    @Test
    void 删除字典_调用service() throws Exception {
        mvc.perform(delete("/dict/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).removeById(9L);
    }
}

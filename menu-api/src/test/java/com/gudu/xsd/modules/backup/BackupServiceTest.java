package com.gudu.xsd.modules.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 备份服务单元测试：mock JdbcTemplate。
 * 覆盖 exportAll（遍历表）、importAll（解包 R{data}、DELETE+INSERT、空表计数 0）。
 *
 * <p>注：BackupService 用字符串拼接表名构造 SQL（不可参数化）。
 * jdbc.update 有两个重载：update(String) 用于 DELETE，update(String, Object[]) 用于 INSERT。
 * 测试用 ArgumentCaptor 在 Invocation 捕获层统一捕获所有 update 调用，避免重载 matcher 歧义。
 */
class BackupServiceTest {

    private JdbcTemplate jdbc;
    private BackupService svc;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        svc = new BackupService(jdbc);
    }

    /** 捕获 jdbc 所有 update 调用的第一个 String 参数（DELETE/INSERT 的 SQL）。 */
    @SuppressWarnings("unchecked")
    private List<String> capturedUpdateSqls() {
        List<String> sqls = new ArrayList<>();
        Mockito.mockingDetails(jdbc).getInvocations().forEach(inv -> {
            if (inv.getMethod().getName().equals("update") && inv.getArguments().length > 0
                    && inv.getArgument(0) instanceof String) {
                sqls.add(inv.getArgument(0));
            }
        });
        return sqls;
    }

    // ---------------- exportAll ----------------

    /** exportAll：遍历全部表，返回 tables 结构 + tableCount。 */
    @Test
    @SuppressWarnings("unchecked")
    void exportAll_遍历全部表_返回结构() {
        when(jdbc.queryForList(anyString())).thenReturn(
                List.of(Map.of("id", 1, "name", "克"), Map.of("id", 2, "name", "个")));

        Map<String, Object> result = svc.exportAll();

        assertThat(result.get("tableCount")).isEqualTo(15); // TABLES 常量共 15 张表
        Map<String, Object> tables = (Map<String, Object>) result.get("tables");
        assertThat(tables).containsOnlyKeys(
                "sys_dict", "nutrition_metric", "user", "member",
                "ingredient", "ingredient_nutrition",
                "dish", "dish_step", "dish_dict", "dish_ingredient",
                "menu", "menu_dish",
                "favorite", "cooking_record", "dish_history");
        assertThat((List<Map<String, Object>>) tables.get("dish")).hasSize(2);
        Mockito.verify(jdbc, Mockito.times(15)).queryForList(anyString());
    }

    // ---------------- importAll ----------------

    /** importAll：直接传 {tables:...} 结构，验证按依赖顺序 DELETE + INSERT，返回每表计数。 */
    @Test
    @SuppressWarnings("unchecked")
    void importAll_直接tables结构_按序删插_返回计数() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> tables = new LinkedHashMap<>();
        // 只灌 2 张表，其余表在 TABLES 中但 tables 里没有 → 计数 0
        tables.put("sys_dict", List.<Map<String, Object>>of(
                Map.of("id", 1, "name", "克", "sort", 1, "dict_group", "unit")));
        tables.put("dish", List.<Map<String, Object>>of(
                Map.of("id", 1, "name", "番茄炒蛋")));
        data.put("tables", tables);

        Map<String, Object> counts = svc.importAll(data);

        List<String> sqls = capturedUpdateSqls();
        // 15 张表各 DELETE 一次 = 15；sys_dict + dish 各 INSERT 一次 = 2
        long deletes = sqls.stream().filter(s -> s.contains("DELETE FROM")).count();
        long inserts = sqls.stream().filter(s -> s.contains("INSERT INTO")).count();
        assertThat(deletes).isEqualTo(15);
        assertThat(inserts).isEqualTo(2);
        // 计数：有数据的表 = 行数，无数据的表 = 0
        assertThat(counts.get("sys_dict")).isEqualTo(1);
        assertThat(counts.get("dish")).isEqualTo(1);
        assertThat(counts.get("menu")).isEqualTo(0);
        assertThat(counts.get("member")).isEqualTo(0);
        assertThat(counts.size()).isEqualTo(15);
    }

    /** importAll：前端传完整 R 响应 {code,msg,data:{tables}}，应解包到 data 层。 */
    @Test
    @SuppressWarnings("unchecked")
    void importAll_外层R包裹_自动解包到data() {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tables", Map.of("dish", List.<Map<String, Object>>of(
                Map.of("id", 9, "name", "测试菜"))));
        wrapped.put("code", 0);
        wrapped.put("msg", "ok");
        wrapped.put("data", inner);

        Map<String, Object> counts = svc.importAll(wrapped);

        List<String> sqls = capturedUpdateSqls();
        long inserts = sqls.stream().filter(s -> s.contains("INSERT INTO")).count();
        assertThat(inserts).isEqualTo(1);
        assertThat(counts.get("dish")).isEqualTo(1);
    }

    /** importAll：空 tables（无任何表数据）→ 全部计数 0，只 DELETE 不 INSERT。 */
    @Test
    void importAll_空tables_全部计数0() {
        Map<String, Object> data = Map.of("tables", Map.of());

        Map<String, Object> counts = svc.importAll(data);

        List<String> sqls = capturedUpdateSqls();
        long deletes = sqls.stream().filter(s -> s.contains("DELETE FROM")).count();
        long inserts = sqls.stream().filter(s -> s.contains("INSERT INTO")).count();
        assertThat(deletes).isEqualTo(15);
        assertThat(inserts).isEqualTo(0);
        assertThat(counts.values()).allSatisfy(v -> assertThat(v).isEqualTo(0));
        assertThat(counts).hasSize(15);
    }

    /** importAll：验证 INSERT SQL 的占位符数量与列数一致。 */
    @Test
    @SuppressWarnings("unchecked")
    void importAll_Insert占位符数等于列数() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("dish", List.<Map<String, Object>>of(
                Map.of("id", 1, "name", "菜", "price", 10)));
        data.put("tables", tables);

        svc.importAll(data);

        List<String> sqls = capturedUpdateSqls();
        String insertSql = sqls.stream().filter(s -> s.contains("INSERT INTO dish")).findFirst().orElseThrow();
        long placeholders = insertSql.chars().filter(c -> c == '?').count();
        assertThat(placeholders).isEqualTo(3); // 3 列
        assertThat(insertSql).contains("INSERT INTO dish");
    }
}

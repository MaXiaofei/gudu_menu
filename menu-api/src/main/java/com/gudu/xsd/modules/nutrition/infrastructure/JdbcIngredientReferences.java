package com.gudu.xsd.modules.nutrition.infrastructure;

import com.gudu.xsd.modules.nutrition.domain.repository.IngredientReferences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link IngredientReferences} 的 JDBC 实现。
 *
 * <p>手写 SQL 不经租户插件：这是平台级操作（运营收编跨家庭合并 / 删除前在用检查），按 ingredient_id 精确匹配，
 * 不带 family_id 条件（TenantGuardTest 白名单登记，审查结论：只按食材 id 改指向，不读取任何业务内容）。
 *
 * <p>合并时的唯一键冲突规则（逐个来源食材依次处理，处理完一个再处理下一个，避免来源之间互相冲突）：
 * <ul>
 *   <li>dish_ingredient（uk dish_id+ingredient_id）：同一道菜已有目标食材 -> 删掉来源那一行（保留目标行的用量）；</li>
 *   <li>ingredient_stock（uk family_id+ingredient_id）：同一家庭已有目标 -> 保留目标行，目标档位取两者中更「足」的
 *       （ENOUGH &gt; LOW &gt; NONE），目标没填保质期而来源有则补上，然后删掉来源行；</li>
 *   <li>shopping_item（uk list_id+ingredient_id+unit_id，单位非空才冲突）：同一清单同一单位已有目标 -> 保留目标行，
 *       目标行已采购状态不变，删掉来源行；</li>
 *   <li>menu_prep_status（uk menu_id+ingredient_id）：同一食集已有目标 -> 保留目标行，删掉来源行；</li>
 *   <li>ingredient_nutrition：来源的营养一律删除（私有食材不带营养；目标自有营养为准）；</li>
 *   <li>无唯一键冲突的表（stock_log / pantry / pantry_change_log / daily_log_item / menu_cook_usage）直接改指向。</li>
 * </ul>
 */
@Repository
public class JdbcIngredientReferences implements IngredientReferences {

    /** 在用检查与改指向都覆盖的引用表：标签 -> 表名。 */
    private static final Map<String, String> USAGE_TABLES = new LinkedHashMap<>();

    static {
        USAGE_TABLES.put("库存", "ingredient_stock");
        USAGE_TABLES.put("采购", "shopping_item");
        USAGE_TABLES.put("菜谱", "dish_ingredient");
        USAGE_TABLES.put("食记", "daily_log_item");
        USAGE_TABLES.put("做菜记录", "menu_cook_usage");
        USAGE_TABLES.put("备菜", "menu_prep_status");
        USAGE_TABLES.put("库存记录", "stock_log");
        USAGE_TABLES.put("库存批次", "pantry");
        USAGE_TABLES.put("库存变更", "pantry_change_log");
    }

    private final JdbcTemplate jdbc;

    /** 注入 @Primary 业务 MySQL 数据源自建 JdbcTemplate（容器里唯一的 JdbcTemplate bean 指向 PG 向量库）。 */
    public JdbcIngredientReferences(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Map<String, Long> usage(Long ingredientId) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (ingredientId == null) return out;
        for (Map.Entry<String, String> e : USAGE_TABLES.entrySet()) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + e.getValue() + " WHERE ingredient_id = ?",
                    Long.class, ingredientId);
            if (n != null && n > 0) out.put(e.getKey(), n);
        }
        return out;
    }

    @Override
    public MergeStats merge(Collection<Long> sourceIds, Long targetId) {
        MergeStats stats = MergeStats.empty();
        for (Long src : sourceIds) {
            if (src == null || src.equals(targetId)) continue;
            mergeOne(src, targetId, stats);
        }
        return stats;
    }

    private void mergeOne(Long src, Long target, MergeStats stats) {
        // 菜谱用料：同菜已有目标 -> 删来源行；其余改指向
        // （DISTINCT 派生表：避免 MySQL 对同表子查询报 1093）
        int dup = jdbc.update("DELETE FROM dish_ingredient WHERE ingredient_id = ? AND dish_id IN "
                + "(SELECT dish_id FROM (SELECT DISTINCT dish_id FROM dish_ingredient WHERE ingredient_id = ?) x)", src, target);
        add(stats.merged(), "dish_ingredient", dup);

        // 库存：同家庭已有目标 -> 档位取更足、补保质期，删来源行
        List<Map<String, Object>> stocks = jdbc.queryForList(
                "SELECT s.id AS sid, s.level AS slevel, s.best_before AS sbb, t.id AS tid, t.level AS tlevel, "
                        + "t.best_before AS tbb FROM ingredient_stock s JOIN ingredient_stock t "
                        + "ON t.family_id = s.family_id AND t.ingredient_id = ? WHERE s.ingredient_id = ?", target, src);
        for (Map<String, Object> r : stocks) {
            String better = levelRank((String) r.get("slevel")) > levelRank((String) r.get("tlevel"))
                    ? (String) r.get("slevel") : (String) r.get("tlevel");
            Object bb = r.get("tbb") != null ? r.get("tbb") : r.get("sbb");
            jdbc.update("UPDATE ingredient_stock SET level = ?, best_before = ? WHERE id = ?",
                    better, bb, ((Number) r.get("tid")).longValue());
            jdbc.update("DELETE FROM ingredient_stock WHERE id = ?", ((Number) r.get("sid")).longValue());
        }
        add(stats.merged(), "ingredient_stock", stocks.size());

        // 采购清单：同清单同单位已有目标 -> 删来源行
        int shopDup = jdbc.update("DELETE FROM shopping_item WHERE ingredient_id = ? AND EXISTS "
                + "(SELECT 1 FROM (SELECT DISTINCT list_id, unit_id FROM shopping_item WHERE ingredient_id = ?) x "
                + "WHERE x.list_id = shopping_item.list_id AND x.unit_id = shopping_item.unit_id)", src, target);
        add(stats.merged(), "shopping_item", shopDup);

        // 备菜状态：同食集已有目标 -> 删来源行
        int prepDup = jdbc.update("DELETE FROM menu_prep_status WHERE ingredient_id = ? AND menu_id IN "
                + "(SELECT menu_id FROM (SELECT DISTINCT menu_id FROM menu_prep_status WHERE ingredient_id = ?) x)", src, target);
        add(stats.merged(), "menu_prep_status", prepDup);

        // 营养：来源的删除
        int nut = jdbc.update("DELETE FROM ingredient_nutrition WHERE ingredient_id = ?", src);
        add(stats.merged(), "ingredient_nutrition", nut);

        // 其余全部改指向目标
        for (String table : List.of("dish_ingredient", "ingredient_stock", "shopping_item", "menu_prep_status",
                "daily_log_item", "menu_cook_usage", "stock_log", "pantry", "pantry_change_log")) {
            int n = jdbc.update("UPDATE " + table + " SET ingredient_id = ? WHERE ingredient_id = ?", target, src);
            add(stats.moved(), table, n);
        }
    }

    static int levelRank(String level) {
        if ("ENOUGH".equals(level)) return 3;
        if ("LOW".equals(level)) return 2;
        if ("NONE".equals(level)) return 1;
        return 0;
    }

    private static void add(Map<String, Integer> m, String table, int n) {
        if (n > 0) m.merge(table, n, Integer::sum);
    }
}

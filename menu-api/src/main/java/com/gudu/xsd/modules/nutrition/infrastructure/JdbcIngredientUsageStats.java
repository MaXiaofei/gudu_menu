package com.gudu.xsd.modules.nutrition.infrastructure;

import com.gudu.xsd.modules.nutrition.domain.repository.IngredientUsageStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

import java.util.HashMap;
import java.util.Map;

/** {@link IngredientUsageStats} 的 JDBC 实现（只读聚合 SQL，单次查询，无 N+1）。 */
@Repository
public class JdbcIngredientUsageStats implements IngredientUsageStats {

    private final JdbcTemplate jdbc;

    /**
     * 注入 @Primary 业务 MySQL 数据源自建 JdbcTemplate——容器里唯一的 JdbcTemplate bean
     * 是 PgVectorConfig 的 pgVectorJdbcTemplate（指向 PG 向量库），直接注入 JdbcTemplate 会打到 PG。
     * 同一 DataSource，参与 Spring 事务（DataSourceUtils）。
     */
    public JdbcIngredientUsageStats(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Map<Long, Integer> cookedUsageByMember(Long memberId) {
        if (memberId == null) return Map.of();
        return toMap("SELECT di.ingredient_id AS id, COUNT(*) AS c FROM cooking_record cr "
                + "JOIN dish_ingredient di ON di.dish_id = cr.dish_id "
                + "WHERE cr.member_id = ? AND di.ingredient_id IS NOT NULL GROUP BY di.ingredient_id", memberId);
    }

    @Override
    public Map<Long, Integer> recipeUsage() {
        return toMap("SELECT di.ingredient_id AS id, COUNT(DISTINCT di.dish_id) AS c FROM dish_ingredient di "
                + "JOIN dish d ON d.id = di.dish_id AND d.deleted = 0 "
                + "WHERE di.ingredient_id IS NOT NULL GROUP BY di.ingredient_id");
    }

    private Map<Long, Integer> toMap(String sql, Object... args) {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getLong("id"), rs.getInt("c"));
        }, args);
        return out;
    }
}

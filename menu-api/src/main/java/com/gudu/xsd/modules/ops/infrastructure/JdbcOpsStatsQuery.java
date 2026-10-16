package com.gudu.xsd.modules.ops.infrastructure;

import com.gudu.xsd.modules.ops.domain.model.DailyCounts;
import com.gudu.xsd.modules.ops.domain.model.OpsStats;
import com.gudu.xsd.modules.ops.domain.repository.OpsStatsQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 运营统计 JDBC 实现：跨表只读聚合（member/dish/cooking_record/daily_log/ai_call_log/review/feedback），
 * 每个指标一条 SQL。activeMembers = cooking_record / daily_log / ai_call_log / review 任一有记录的去重成员。
 */
@Repository
public class JdbcOpsStatsQuery implements OpsStatsQuery {

    static final String ACCOUNT_COND = "deleted = 0 AND (password_hash IS NOT NULL OR openid IS NOT NULL)";

    /** 活跃行为并集（day, member_id），参数：since ×4。 */
    static final String ACTIVITY_UNION = "SELECT DATE(cooked_at) AS d, member_id FROM cooking_record WHERE cooked_at >= ? "
            + "UNION ALL SELECT DATE(create_time), member_id FROM daily_log WHERE create_time >= ? "
            + "UNION ALL SELECT DATE(create_time), member_id FROM ai_call_log WHERE create_time >= ? "
            + "UNION ALL SELECT DATE(create_time), member_id FROM review WHERE create_time >= ?";

    private final JdbcTemplate jdbc;

    /**
     * 注入 @Primary 业务 MySQL 数据源自建 JdbcTemplate——容器里唯一的 JdbcTemplate bean
     * 是 PgVectorConfig 的 pgVectorJdbcTemplate（指向 PG 向量库），直接注入 JdbcTemplate 会打到 PG。
     * 同一 DataSource，参与 Spring 事务（DataSourceUtils）。
     */
    public JdbcOpsStatsQuery(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public OpsStats.Totals totals() {
        return new OpsStats.Totals(
                count("SELECT COUNT(*) FROM member WHERE deleted = 0"),
                count("SELECT COUNT(*) FROM member WHERE " + ACCOUNT_COND),
                count("SELECT COUNT(*) FROM dish WHERE deleted = 0"),
                count("SELECT COUNT(*) FROM cooking_record"),
                count("SELECT COUNT(*) FROM feedback WHERE status = 'NEW'"));
    }

    @Override
    public OpsStats.Last7d summarySince(LocalDate since) {
        LocalDateTime from = since.atStartOfDay();
        BigDecimal cost = jdbc.queryForObject(
                "SELECT COALESCE(SUM(cost), 0) FROM ai_call_log WHERE create_time >= ?", BigDecimal.class, from);
        return new OpsStats.Last7d(
                count("SELECT COUNT(*) FROM member WHERE " + ACCOUNT_COND + " AND create_time >= ?", from),
                count("SELECT COUNT(DISTINCT member_id) FROM (" + ACTIVITY_UNION + ") t WHERE member_id IS NOT NULL",
                        from, from, from, from),
                count("SELECT COUNT(*) FROM cooking_record WHERE cooked_at >= ?", from),
                count("SELECT COUNT(*) FROM ai_call_log WHERE create_time >= ?", from),
                cost == null ? BigDecimal.ZERO : cost);
    }

    @Override
    public DailyCounts dailySince(LocalDate since) {
        LocalDateTime from = since.atStartOfDay();
        return new DailyCounts(
                daily("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM member WHERE " + ACCOUNT_COND
                        + " AND create_time >= ? GROUP BY DATE(create_time)", from),
                daily("SELECT d, COUNT(DISTINCT member_id) AS c FROM (" + ACTIVITY_UNION
                        + ") t WHERE member_id IS NOT NULL GROUP BY d", from, from, from, from),
                daily("SELECT DATE(cooked_at) AS d, COUNT(*) AS c FROM cooking_record WHERE cooked_at >= ? "
                        + "GROUP BY DATE(cooked_at)", from),
                daily("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM ai_call_log WHERE create_time >= ? "
                        + "GROUP BY DATE(create_time)", from));
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private Map<LocalDate, Long> daily(String sql, Object... args) {
        Map<LocalDate, Long> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            Date d = rs.getDate("d");
            if (d != null) out.put(d.toLocalDate(), rs.getLong("c"));
        }, args);
        return out;
    }
}

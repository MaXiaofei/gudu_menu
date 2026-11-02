package com.gudu.xsd.modules.aiguard.infrastructure;

import com.gudu.xsd.modules.aiguard.domain.repository.AiGuardConfigRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ai_guard_config / ai_guard_config_log（V100）的 JDBC 实现。 */
@Repository
public class JdbcAiGuardConfigRepository implements AiGuardConfigRepository {

    private final JdbcTemplate jdbc;

    /** 注入 @Primary 业务 MySQL 数据源自建 JdbcTemplate（容器里唯一的 JdbcTemplate bean 指向 PG 向量库）。 */
    public JdbcAiGuardConfigRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Map<String, String> loadAll() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT config_key, config_value FROM ai_guard_config",
                rs -> {
                    out.put(rs.getString(1), rs.getString(2));
                });
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(List<Change> changes, Long operatorId, String operatorName, LocalDateTime at) {
        Timestamp ts = Timestamp.valueOf(at);
        for (Change c : changes) {
            jdbc.update("INSERT INTO ai_guard_config(config_key, config_value, updated_by, updated_by_name, updated_at) "
                            + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), "
                            + "updated_by = VALUES(updated_by), updated_by_name = VALUES(updated_by_name), "
                            + "updated_at = VALUES(updated_at)",
                    c.key(), c.newValue(), operatorId, operatorName, ts);
            jdbc.update("INSERT INTO ai_guard_config_log(config_key, old_value, new_value, operator_id, operator_name, "
                            + "create_time) VALUES (?,?,?,?,?,?)",
                    c.key(), c.oldValue(), c.newValue(), operatorId, operatorName, ts);
        }
    }

    @Override
    public List<ChangeLog> recentLogs(int limit) {
        return jdbc.query("SELECT config_key, old_value, new_value, operator_id, operator_name, create_time "
                        + "FROM ai_guard_config_log ORDER BY id DESC LIMIT ?",
                (rs, i) -> new ChangeLog(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getTimestamp(6).toLocalDateTime()),
                limit);
    }
}

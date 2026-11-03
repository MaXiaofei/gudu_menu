package com.gudu.xsd.prode2e;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 全站配置表快照：用例要改 sys_switch / ai_guard_config / ai_route 等全站配置前先拍快照，
 * 用例的 finally 里调 {@link #restoreOrFail()} 还原；还原后会再读一遍比对，
 * 对不上就抛 AssertionError 并把原值整行打印出来（便于人工恢复）。
 *
 * <p>只动「快照中存在 / 测试期间新增」的配置行，不碰其他表；INSERT / UPDATE / DELETE 都按主键（key 列）精确定位。
 * 注意：这是直接写库的兜底还原。凡是有 API 的全站配置（开关、额度、网关路由），用例应先用 API 改回（能触发运行中实例的缓存 / 广播刷新），
 * 再调本方法兜底校验。
 */
public final class ProdE2ESnapshot {

    private final JdbcTemplate jdbc;
    private final String table;
    private final String keyColumn;
    private final List<Map<String, Object>> original;

    private ProdE2ESnapshot(JdbcTemplate jdbc, String table, String keyColumn) {
        this.jdbc = jdbc;
        this.table = table;
        this.keyColumn = keyColumn;
        this.original = read();
    }

    public static ProdE2ESnapshot of(JdbcTemplate jdbc, String table, String keyColumn) {
        return new ProdE2ESnapshot(jdbc, table, keyColumn);
    }

    /** 原值（可读文本，失败信息里用）。 */
    public String describe() {
        return table + " 原值=" + original;
    }

    public List<Map<String, Object>> originalRows() {
        return original;
    }

    private List<Map<String, Object>> read() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT * FROM `" + table + "`")) {
            rows.add(new LinkedHashMap<>(r));
        }
        return rows;
    }

    private Map<Object, Map<String, Object>> byKey(List<Map<String, Object>> rows) {
        Map<Object, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) m.put(String.valueOf(r.get(keyColumn)), r);
        return m;
    }

    /** 还原到快照；还原后复核，不一致抛 AssertionError（含原值）。 */
    public void restoreOrFail() {
        Map<Object, Map<String, Object>> orig = byKey(original);
        try {
            Map<Object, Map<String, Object>> now = byKey(read());
            for (Map.Entry<Object, Map<String, Object>> e : now.entrySet()) {
                if (!orig.containsKey(e.getKey())) {
                    jdbc.update("DELETE FROM `" + table + "` WHERE `" + keyColumn + "` = ?", e.getValue().get(keyColumn));
                }
            }
            for (Map.Entry<Object, Map<String, Object>> e : orig.entrySet()) {
                Map<String, Object> want = e.getValue();
                Map<String, Object> cur = now.get(e.getKey());
                if (cur == null) {
                    insert(want);
                } else if (!Objects.equals(cur, want)) {
                    update(want);
                }
            }
        } catch (RuntimeException ex) {
            throw new AssertionError("还原全站配置失败，请人工恢复！" + describe() + "；原因：" + ex, ex);
        }
        Map<Object, Map<String, Object>> after = byKey(read());
        if (!after.equals(orig)) {
            throw new AssertionError("还原后与快照不一致，请人工恢复！" + describe() + "；现值=" + after.values());
        }
    }

    private void insert(Map<String, Object> row) {
        StringBuilder cols = new StringBuilder();
        StringBuilder qs = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> c : row.entrySet()) {
            if (cols.length() > 0) {
                cols.append(',');
                qs.append(',');
            }
            cols.append('`').append(c.getKey()).append('`');
            qs.append('?');
            args.add(c.getValue());
        }
        jdbc.update("INSERT INTO `" + table + "`(" + cols + ") VALUES (" + qs + ")", args.toArray());
    }

    private void update(Map<String, Object> row) {
        StringBuilder set = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> c : row.entrySet()) {
            if (c.getKey().equalsIgnoreCase(keyColumn)) continue;
            if (set.length() > 0) set.append(',');
            set.append('`').append(c.getKey()).append("` = ?");
            args.add(c.getValue());
        }
        args.add(row.get(keyColumn));
        jdbc.update("UPDATE `" + table + "` SET " + set + " WHERE `" + keyColumn + "` = ?", args.toArray());
    }
}

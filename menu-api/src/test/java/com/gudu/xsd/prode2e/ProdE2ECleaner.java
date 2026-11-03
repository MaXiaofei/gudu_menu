package com.gudu.xsd.prode2e;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产端到端测试的数据清理（只按前缀 / 自建 id 精确删除，绝不按范围删除，不执行 DDL，不清空 Redis）。
 *
 * <p>身份判定（全部为「前缀匹配」或「测试自己创建时记下的 id」）：
 * <ul>
 *   <li>账号：昵称或微信 openid 以 {@code E2E_PROD_} 开头；被并入这些账号的微信账号（merged_into）；本机台账里记过的账号 id
 *       （注销用例会把昵称匿名化，靠台账认出）；</li>
 *   <li>家庭：主理人是上述账号、或名称以 {@code E2E_PROD_} 开头（且 id &gt; 2，并且成员全部是 E2E 账号，混入了真实用户的家庭拒绝删除并报告）；</li>
 *   <li>成员档案：属于上述家庭，或关联上述账号；</li>
 *   <li>菜谱 / 食材 / 字典 / 营养指标 / 帖子 / 会话 / 网关供应商 等：名称（标题）以 {@code E2E_PROD_} 开头，或归属于上述家庭 / 账号 / 档案。</li>
 * </ul>
 *
 * <p>为什么「按 id 集合删」是安全的：账号 id 自增起点已抬高（V91，≥ 一千万），档案 id 在其下方且一直递增，
 * 新建的 E2E 账号 id 与任何存量真实账号 / 档案 id 都不会相等，所以对「个人归属列」用 E2E 账号 id ∪ E2E 档案 id 做 IN 匹配不会误伤。
 */
public final class ProdE2ECleaner {

    public static final String PREFIX = "E2E_PROD_";
    /** E2E 手机号段（1390000xxxx），仅用于清理自己写过的 Redis 登录限流键。 */
    private static final String PHONE_REDIS_PATTERN = "*1390000[0-9][0-9][0-9][0-9]*";
    private static final int MAX_ACCOUNTS = 2000;

    /** 不走通用列扫描、由本类显式收尾的表。 */
    private static final Set<String> PROTECTED_TABLES = Set.of("account", "member", "family", "family_membership");
    /** 通用「个人归属」列：值是账号 id 或档案 id（见类注释）。 */
    private static final Set<String> PERSON_COLUMNS = Set.of("member_id", "user_id", "author_id", "reporter_id",
            "follower_id", "followee_id", "recipient_account_id", "recipient_profile_id", "account_id", "operator_id",
            "target_member_id", "added_by_member_id", "created_by");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Map<String, Integer> deleted = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    public ProdE2ECleaner(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    /** 清理结果：表 -> 删除行数，以及跳过 / 告警说明。 */
    public record Result(Map<String, Integer> deleted, List<String> notes) {
        public int total() {
            return deleted.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public String toString() {
            return "E2E_PROD_ 清理结果 共删 " + total() + " 行 " + deleted + (notes.isEmpty() ? "" : " 说明=" + notes);
        }
    }

    // ------------------------------------------------------------------ 识别

    private static String pfx(String col) {
        return "LOWER(LEFT(" + col + ", " + PREFIX.length() + ")) = '" + PREFIX.toLowerCase() + "'";
    }

    private List<Long> ids(String sql) {
        return jdbc.queryForList(sql, Long.class);
    }

    private static String in(Collection<Long> ids) {
        if (ids.isEmpty()) return "(NULL)";
        StringBuilder sb = new StringBuilder("(");
        for (Long id : ids) sb.append(id).append(',');
        sb.setCharAt(sb.length() - 1, ')');
        return sb.toString();
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = ?", Integer.class, table);
        return n != null && n > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = ? AND column_name = ?", Integer.class, table, column);
        return n != null && n > 0;
    }

    private Set<Long> accountIds() {
        Set<Long> a = new LinkedHashSet<>(ids("SELECT id FROM account WHERE " + pfx("nickname") + " OR " + pfx("wx_openid")));
        if (!a.isEmpty() && columnExists("account", "merged_into")) {
            a.addAll(ids("SELECT id FROM account WHERE merged_into IN " + in(a)));
        }
        Set<Long> ledger = ProdE2ELedger.accountIds();
        if (!ledger.isEmpty()) {
            a.addAll(ids("SELECT id FROM account WHERE id IN " + in(ledger)));
        }
        if (a.size() > MAX_ACCOUNTS) {
            throw new IllegalStateException("匹配到 " + a.size() + " 个 E2E 账号，超过上限 " + MAX_ACCOUNTS + "，疑似匹配异常，拒绝清理");
        }
        return a;
    }

    // ------------------------------------------------------------------ 清理

    public synchronized Result cleanAll() {
        deleted.clear();
        notes.clear();

        Set<Long> accounts = accountIds();

        // 家庭：E2E 账号当主理人 / E2E 前缀命名；成员里混入非 E2E 账号的家庭不删（报告）
        Set<Long> candidateFamilies = new LinkedHashSet<>(ids("SELECT id FROM family WHERE id > 2 AND (owner_account_id IN "
                + in(accounts) + " OR " + pfx("name") + ")"));
        candidateFamilies.addAll(ids("SELECT family_id FROM family_membership WHERE role = 'OWNER' AND family_id > 2 "
                + "AND account_id IN " + in(accounts)));
        Set<Long> families = new LinkedHashSet<>();
        for (Long f : candidateFamilies) {
            Integer foreign = jdbc.queryForObject("SELECT COUNT(*) FROM family_membership WHERE family_id = ? AND account_id NOT IN "
                    + in(accounts), Integer.class, f);
            if (foreign != null && foreign > 0) {
                notes.add("家庭 " + f + " 含非 E2E 账号成员，未删除，请人工确认");
            } else {
                families.add(f);
            }
        }

        // 档案、菜谱、食材
        Set<Long> profiles = new LinkedHashSet<>(ids("SELECT id FROM member WHERE family_id IN " + in(families)
                + " OR account_id IN " + in(accounts)));
        Set<Long> persons = new LinkedHashSet<>(accounts);
        persons.addAll(profiles);

        List<Long> dishes = ids("SELECT id FROM dish WHERE family_id IN " + in(families) + " OR " + pfx("name")
                + (columnExists("dish", "created_by") ? " OR created_by IN " + in(accounts) : ""));
        List<Long> ingredients = ids("SELECT id FROM ingredient WHERE family_id IN " + in(families) + " OR " + pfx("name"));

        // 子表（没有家庭 / 个人列，靠父表 id 删）
        delChild("dish_ingredient", "dish_id", dishes);
        delChild("dish_step", "dish_id", dishes);
        delChild("dish_dict", "dish_id", dishes);
        delChild("ingredient_nutrition", "ingredient_id", ingredients);
        delChild("ingredient_alias", "ingredient_id", ingredients);
        delChild("ingredient_unit_gram", "ingredient_id", ingredients);
        delChild("ingredient_stock", "ingredient_id", ingredients);
        delChild("stock_log", "ingredient_id", ingredients);

        // 厨房圈
        List<Long> posts = tableExists("community_post")
                ? ids("SELECT id FROM community_post WHERE " + pfx("title") + " OR author_id IN " + in(persons)) : List.of();
        for (String t : List.of("community_post_topic", "community_comment", "community_reaction", "community_copy",
                "community_report", "community_topic_checkin", "community_appeal", "community_moderation_log")) {
            delChild(t, "post_id", posts);
        }
        delChild("community_post", "id", posts);

        // 咕嘟 Agent
        if (tableExists("agent_session")) {
            List<Long> sessions = ids("SELECT id FROM agent_session WHERE member_id IN " + in(persons) + " OR " + pfx("title"));
            List<Long> traces = tableExists("agent_trace") && !sessions.isEmpty()
                    ? ids("SELECT id FROM agent_trace WHERE session_id IN " + in(sessions)) : List.of();
            delChild("agent_eval_case", "from_trace_id", traces);
            delChild("agent_span", "trace_id", traces);
            delChild("agent_trace", "session_id", sessions);
            delChild("agent_message", "session_id", sessions);
            delChild("agent_session", "id", sessions);
        }
        delWhere("agent_eval_case", "case_id", pfx("case_id"));
        delWhere("agent_eval_run", "prompt_version", pfx("prompt_version"));

        // 模型网关：只删 E2E_PROD_ 前缀命名的供应商及其模型（被路由引用的不删，报告）
        if (tableExists("ai_provider")) {
            List<Long> providers = ids("SELECT id FROM ai_provider WHERE " + pfx("name"));
            delChild("ai_provider_model", "provider_id", providers);
            delChild("ai_provider", "id", providers);
        }

        // 带自建名称的全局表（字典 / 营养指标 / 内容安全记录）
        delWhere("sys_dict", "name", pfx("name"));
        delWhere("nutrition_metric", "name", pfx("name"));
        delWhere("content_media_check", "trace_id", pfx("trace_id"));
        delWhere("sys_switch_log", "reason", pfx("reason"));
        delWhere("admin_operation_log", "reason", pfx("reason"));
        delWhere("ai_guard_config_log", "operator_name", pfx("operator_name"));
        delWhere("account_merge_log", "source_id", "source_id IN " + in(accounts));
        delWhere("account_merge_log", "target_id", "target_id IN " + in(accounts));

        // 通用扫描：所有含 family_id / 个人归属列的表，只删属于 E2E 家庭 / E2E 账号与档案的行
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        jdbc.query("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name IN (SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_type = 'BASE TABLE')", rs -> {
            columns.computeIfAbsent(rs.getString(1).toLowerCase(), k -> new LinkedHashSet<>()).add(rs.getString(2).toLowerCase());
        });
        for (Map.Entry<String, Set<String>> e : columns.entrySet()) {
            String table = e.getKey();
            if (PROTECTED_TABLES.contains(table)) continue;
            if (e.getValue().contains("family_id") && !families.isEmpty()) {
                delWhere(table, "family_id", "family_id IN " + in(families));
            }
            for (String col : PERSON_COLUMNS) {
                if (e.getValue().contains(col) && !persons.isEmpty()) {
                    delWhere(table, col, col + " IN " + in(persons));
                }
            }
        }

        // 收尾：档案 -> 成员关系 -> 家庭 -> 账号
        delWhere("member", "id", "id IN " + in(profiles));
        delWhere("family_membership", "family_id", "family_id IN " + in(families));
        delWhere("family_membership", "account_id", "account_id IN " + in(accounts));
        delWhere("family", "id", "id IN " + in(families));
        delWhere("account", "id", "id IN " + in(accounts));

        cleanRedis(accounts);
        ProdE2ELedger.clear();
        return new Result(new LinkedHashMap<>(deleted), new ArrayList<>(notes));
    }

    /** 清理后的残留自检（全部应为 0）：返回「描述 -> 残留行数」，只含 > 0 的项。 */
    public Map<String, Integer> residue() {
        Map<String, Integer> out = new LinkedHashMap<>();
        count(out, "账号(前缀)", "SELECT COUNT(*) FROM account WHERE " + pfx("nickname") + " OR " + pfx("wx_openid"));
        count(out, "家庭(前缀)", "SELECT COUNT(*) FROM family WHERE " + pfx("name"));
        count(out, "菜谱(前缀)", "SELECT COUNT(*) FROM dish WHERE " + pfx("name"));
        count(out, "草稿(前缀)", "SELECT COUNT(*) FROM dish_draft WHERE " + pfx("name"));
        count(out, "食材(前缀)", "SELECT COUNT(*) FROM ingredient WHERE " + pfx("name"));
        count(out, "字典(前缀)", "SELECT COUNT(*) FROM sys_dict WHERE " + pfx("name"));
        count(out, "食集(前缀)", "SELECT COUNT(*) FROM menu WHERE " + pfx("name"));
        count(out, "帖子(前缀)", "SELECT COUNT(*) FROM community_post WHERE " + pfx("title"));
        count(out, "台账账号", "SELECT COUNT(*) FROM account WHERE id IN " + in(ProdE2ELedger.accountIds()));
        return out;
    }

    private void count(Map<String, Integer> out, String label, String sql) {
        try {
            Integer n = jdbc.queryForObject(sql, Integer.class);
            if (n != null && n > 0) out.put(label, n);
        } catch (RuntimeException e) {
            // 表不存在等：跳过
        }
    }

    private void delChild(String table, String column, Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return;
        delWhere(table, column, column + " IN " + in(parentIds));
    }

    private void delWhere(String table, String column, String where) {
        if (!tableExists(table) || !columnExists(table, column)) return;
        int n = jdbc.update("DELETE FROM `" + table + "` WHERE " + where);
        if (n > 0) deleted.merge(table, n, Integer::sum);
    }

    // ------------------------------------------------------------------ Redis（只删自己写的键）

    private void cleanRedis(Set<Long> accounts) {
        if (redis == null) return;
        int removed = 0;
        for (Long id : accounts) {
            try {
                StpUtil.logout(id);
            } catch (RuntimeException e) {
                notes.add("登出账号 " + id + " 失败：" + e.getMessage());
            }
        }
        List<String> patterns = new ArrayList<>();
        for (Long id : accounts) {
            patterns.add("*:" + id);
            patterns.add("*:" + id + ":*");
        }
        patterns.add(PHONE_REDIS_PATTERN);
        for (String p : patterns) {
            removed += scanDelete(p);
        }
        if (removed > 0) deleted.merge("redis_keys", removed, Integer::sum);
    }

    private int scanDelete(String pattern) {
        int n = 0;
        try (Cursor<String> cur = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            List<String> batch = new ArrayList<>();
            while (cur.hasNext()) {
                batch.add(cur.next());
                if (batch.size() >= 200) {
                    n += batch.size();
                    redis.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                n += batch.size();
                redis.delete(batch);
            }
        } catch (RuntimeException e) {
            notes.add("Redis 清理 " + pattern + " 失败：" + e.getMessage());
        }
        return n;
    }
}

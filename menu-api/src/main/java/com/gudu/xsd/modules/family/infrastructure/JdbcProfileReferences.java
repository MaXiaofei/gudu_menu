package com.gudu.xsd.modules.family.infrastructure;

import com.gudu.xsd.modules.family.domain.repository.ProfileReferences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ProfileReferences} 的 JDBC 实现。手写 SQL 不经租户插件：账号合并是平台级操作，
 * 改指只按档案 id（member.id）精确匹配、探测只按 family_id 精确匹配，不读取任何业务内容
 * （TenantGuardTest 白名单登记）。
 *
 * <p>改指的列（均为「档案 id」，见 openspec multi-tenant-family design 9.2 审计表）：
 * cooking_record.member_id、review.member_id、daily_log.member_id、menu.target_member_id、
 * dish_draft.user_id、meal_plan_draft.member_id、ai_call_log.member_id、notification.recipient_profile_id；
 * favorite 有 UNIQUE(member_id, dish_id)，先删掉 from 里目标已收藏的菜再改指。
 * agent_session / agent_message / agent_trace 由 agent 上下文监听 ProfileMerged 自行改指。
 * <b>不改</b> menu_dish.added_by_member_id（混存账号 / 档案 id，仅作留痕、无读取路径，见 design 9.2）。
 */
@Repository
public class JdbcProfileReferences implements ProfileReferences {

    /** 表 -> 档案 id 列。 */
    private static final Map<String, String> PROFILE_COLUMNS = new LinkedHashMap<>();

    /** 业务数据探测：表 -> 是否带 deleted 逻辑删除列（均有 family_id）。 */
    private static final Map<String, Boolean> BUSINESS_TABLES = new LinkedHashMap<>();

    static {
        PROFILE_COLUMNS.put("cooking_record", "member_id");
        PROFILE_COLUMNS.put("review", "member_id");
        PROFILE_COLUMNS.put("daily_log", "member_id");
        PROFILE_COLUMNS.put("menu", "target_member_id");
        PROFILE_COLUMNS.put("dish_draft", "user_id");
        PROFILE_COLUMNS.put("meal_plan_draft", "member_id");
        PROFILE_COLUMNS.put("ai_call_log", "member_id");
        PROFILE_COLUMNS.put("notification", "recipient_profile_id");

        BUSINESS_TABLES.put("dish", true);               // 家庭私有菜谱（family_id 非空）
        BUSINESS_TABLES.put("ingredient", true);         // 家庭私有食材
        BUSINESS_TABLES.put("menu", true);               // 食集 / 餐程
        BUSINESS_TABLES.put("menu_template", false);
        BUSINESS_TABLES.put("meal_plan", true);
        BUSINESS_TABLES.put("meal_plan_draft", false);
        BUSINESS_TABLES.put("ingredient_stock", false);  // 我家余量
        BUSINESS_TABLES.put("pantry", true);
        BUSINESS_TABLES.put("shopping_list", true);      // 采购清单
        BUSINESS_TABLES.put("cooking_record", false);    // 做菜记录
        BUSINESS_TABLES.put("daily_log", true);          // 食记
        BUSINESS_TABLES.put("review", true);
        BUSINESS_TABLES.put("favorite", false);
        BUSINESS_TABLES.put("dish_draft", false);
    }

    private final JdbcTemplate jdbc;

    /** 注入 @Primary 业务 MySQL 数据源自建 JdbcTemplate（容器里唯一的 JdbcTemplate bean 指向 PG 向量库）。 */
    public JdbcProfileReferences(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public int reassign(Long from, Long to) {
        if (from == null || to == null || from.equals(to)) return 0;
        int n = 0;
        // 收藏：目标已收藏的菜，去掉 from 的那条，其余改指（派生表绕开 MySQL「不能对同表子查询」限制）
        n += jdbc.update("DELETE FROM favorite WHERE member_id = ? AND dish_id IN "
                + "(SELECT dish_id FROM (SELECT dish_id FROM favorite WHERE member_id = ?) t)", from, to);
        n += jdbc.update("UPDATE favorite SET member_id = ? WHERE member_id = ?", to, from);
        for (Map.Entry<String, String> e : PROFILE_COLUMNS.entrySet()) {
            n += jdbc.update("UPDATE " + e.getKey() + " SET " + e.getValue() + " = ? WHERE " + e.getValue() + " = ?",
                    to, from);
        }
        return n;
    }

    @Override
    public boolean familyHasBusinessData(Long familyId) {
        if (familyId == null) return false;
        for (Map.Entry<String, Boolean> e : BUSINESS_TABLES.entrySet()) {
            String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM " + e.getKey() + " WHERE family_id = ?"
                    + (e.getValue() ? " AND deleted = 0" : "") + " LIMIT 1) t";
            Long n = jdbc.queryForObject(sql, Long.class, familyId);
            if (n != null && n > 0) return true;
        }
        return false;
    }
}

package com.gudu.xsd.common.tenant;

import java.util.Locale;
import java.util.Set;

/**
 * 家庭域表登记（白名单）：这些表由 MyBatis-Plus TenantLineInnerInterceptor 自动追加 {@code family_id = 当前家庭}，
 * INSERT 自动补 family_id。不在名单内的表视为全局表（食材/营养/字典/单位换算/账号/家庭/社区/AI 网关等）。
 *
 * <p>dish / dish_ingredient / dish_step 不在名单：可见性「公共 + 本家」由 dish 上下文手写（design §4.3）。
 * <p>新增家庭域表必须登记于此，并同步迁移脚本加 family_id；ArchUnit {@code TenantGuardTest} 双向校验
 * 「登记表的 PO 必须有 familyId 字段」与「有 familyId 字段的 PO 必须登记（dish 除外）」。
 */
public final class TenantTables {

    public static final String COLUMN = "family_id";

    public static final Set<String> TABLES = Set.of(
            "member",
            "menu", "menu_dish", "menu_prep_status", "menu_activity", "menu_invite", "menu_join",
            "menu_cook_usage", "menu_template",
            "ingredient_stock", "pantry", "pantry_change_log", "stock_log",
            "shopping_list", "shopping_item",
            "cooking_record", "favorite",
            "daily_log", "daily_log_item",
            "review", "review_score",
            "dish_draft", "dish_history",
            "meal_plan", "meal_plan_item", "meal_plan_draft", "meal_plan_slot",
            "notification");

    private TenantTables() {
    }

    public static boolean isTenantTable(String table) {
        if (table == null) return false;
        String t = table.replace("`", "").trim().toLowerCase(Locale.ROOT);
        int dot = t.lastIndexOf('.');
        if (dot >= 0) t = t.substring(dot + 1);
        return TABLES.contains(t);
    }
}

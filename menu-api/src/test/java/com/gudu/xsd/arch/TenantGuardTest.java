package com.gudu.xsd.arch;

import com.gudu.xsd.common.tenant.TenantTables;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户隔离守护（multi-tenant-family C.4）：
 * <ol>
 *   <li>登记为家庭域表（{@link TenantTables}）的持久化对象（@TableName）必须声明 {@code familyId} 字段；</li>
 *   <li>声明了 {@code familyId} 字段的持久化对象，其表必须登记为家庭域表（防漏登记 → 漏过滤）；
 *       例外：dish（公共 / 本家可见性手写）、family_membership / family_invitation（家庭本身的全局表）；</li>
 *   <li>手写 SQL（MyBatis @Select/@Update/@Insert/@Delete、直接用 JdbcTemplate）不经 / 不完全经租户插件改写，
 *       必须在 {@link #HANDWRITTEN_SQL_WHITELIST} 登记并经代码审查确认自带 family_id 条件或只访问全局表。</li>
 * </ol>
 */
class TenantGuardTest {

    static final Set<String> FAMILY_ID_EXEMPT_TABLES = Set.of("dish", "family_membership", "family_invitation");

    /** 手写 SQL 登记（类全名 → 审查结论见注释）。新增手写 SQL 必须先在此登记。 */
    static final Set<String> HANDWRITTEN_SQL_WHITELIST = Set.of(
            // PG 向量库（独立数据源，不含家庭域表）
            "com.gudu.xsd.config.PgVectorConfig",
            // 运营统计：平台级聚合（仅超管可调），全局计数
            "com.gudu.xsd.modules.ops.infrastructure.JdbcOpsStatsQuery",
            // 运营后台账号目录：只读全局表 account / account_ban / family_membership / family（不碰家庭域表）
            "com.gudu.xsd.modules.backoffice.infrastructure.JdbcAccountDirectoryQuery",
            // 食材使用频次：显式带 cooking_record.family_id 与 dish 可见性条件
            "com.gudu.xsd.modules.nutrition.infrastructure.JdbcIngredientUsageStats",
            // 厨房圈：全局 UGC 表（community_*），不属于任何家庭
            "com.gudu.xsd.modules.community.infrastructure.mapper.CommunityRelationMapper",
            "com.gudu.xsd.modules.community.infrastructure.mapper.CommunityAppealMapper",
            "com.gudu.xsd.modules.community.infrastructure.mapper.CommunityPostMapper",
            "com.gudu.xsd.modules.community.infrastructure.mapper.CommunityModerationLogMapper",
            "com.gudu.xsd.modules.community.infrastructure.mapper.CommunityTopicCheckinMapper",
            "com.gudu.xsd.modules.community.infrastructure.mapper.SysSwitchMapper",
            // 咕嘟助手 / 问咕嘟紧急关停开关：sys_switch 全局表（无 family_id），按 switch_key upsert
            "com.gudu.xsd.modules.featureswitch.infrastructure.mapper.FeatureSwitchMapper",
            // Agent 会话保存期限清理（C.1）：agent_session 全局表（按档案归属），手写以绕过逻辑删除做物理删除
            "com.gudu.xsd.modules.agent.infrastructure.mapper.AgentSessionMapper",
            // 字段加密存量迁移（B.3）：平台级运维一次性全表处理 account / member（按主键分批，不按家庭）
            "com.gudu.xsd.common.crypto.FieldEncryptionMigrator");

    static final Set<String> SQL_ANNOTATIONS = Set.of(
            "org.apache.ibatis.annotations.Select", "org.apache.ibatis.annotations.Update",
            "org.apache.ibatis.annotations.Insert", "org.apache.ibatis.annotations.Delete");

    private static JavaClasses classes() {
        return new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.gudu.xsd");
    }

    private static String tableOf(JavaClass c) {
        return c.tryGetAnnotationOfType("com.baomidou.mybatisplus.annotation.TableName")
                .map(a -> String.valueOf(a.get("value").orElse("")))
                .orElse(null);
    }

    private static boolean hasFamilyIdField(JavaClass c) {
        for (JavaField f : c.getAllFields()) {
            if ("familyId".equals(f.getName())) return true;
        }
        return false;
    }

    @Test
    void 家庭域表的持久化对象必须有familyId_有familyId的必须登记为家庭域表() {
        List<String> failures = new ArrayList<>();
        int tenantPos = 0;
        for (JavaClass c : classes()) {
            String table = tableOf(c);
            if (table == null || table.isEmpty()) continue;
            boolean tenant = TenantTables.isTenantTable(table);
            boolean has = hasFamilyIdField(c);
            if (tenant) tenantPos++;
            if (tenant && !has) failures.add(c.getName() + " 映射家庭域表 " + table + " 但缺少 familyId 字段");
            if (!tenant && has && !FAMILY_ID_EXEMPT_TABLES.contains(table)) {
                failures.add(c.getName() + " 有 familyId 字段但表 " + table + " 未登记到 TenantTables");
            }
        }
        assertThat(tenantPos).as("至少应识别到若干家庭域 PO（规则自检）").isGreaterThan(10);
        assertThat(failures).isEmpty();
    }

    @Test
    void 手写SQL必须登记() {
        List<String> failures = new ArrayList<>();
        for (JavaClass c : classes()) {
            boolean handwritten = c.getDirectDependenciesFromSelf().stream()
                    .anyMatch(d -> d.getTargetClass().getName().equals("org.springframework.jdbc.core.JdbcTemplate"));
            for (JavaMethod m : c.getMethods()) {
                for (String a : SQL_ANNOTATIONS) {
                    if (m.isAnnotatedWith(a)) handwritten = true;
                }
            }
            if (handwritten && !HANDWRITTEN_SQL_WHITELIST.contains(c.getName())) {
                failures.add(c.getName() + " 含手写 SQL，须确认带 family_id 条件后登记到 TenantGuardTest 白名单");
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void 白名单项都真实存在_防止过期登记() {
        Set<String> names = new java.util.HashSet<>();
        classes().forEach(c -> names.add(c.getName()));
        assertThat(names).containsAll(HANDWRITTEN_SQL_WHITELIST);
    }
}

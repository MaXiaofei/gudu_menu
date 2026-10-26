package com.gudu.xsd.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gudu.xsd.common.tenant.DishVisibility;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;

import java.util.Locale;
import java.util.Set;

/**
 * 菜谱可见性兜底（design §4.3，E.1）：凡经 MyBatis 访问菜谱及其子表的 SQL（含跨模块直接用 Mapper 的存量代码）一律改写：
 * <ul>
 *   <li>{@code dish}：追加 {@code (dish.family_id IS NULL OR dish.family_id = 当前家庭)}；游客 / 无上下文只看公共菜谱；</li>
 *   <li>子表 {@code dish_ingredient / dish_step / dish_dict}：追加
 *       {@code EXISTS (SELECT 1 FROM dish vd WHERE vd.id = 子表.dish_id AND 可见性)}，
 *       系统化兜底「拿 dishId 直接读子表」——别家私有菜谱的用料 / 步骤 / 标签读不到、改不动、删不掉；</li>
 *   <li>SELECT / UPDATE / DELETE 都改写；INSERT 不改写（新建归属与「公共菜谱仅平台运营可写」由
 *       DishService / DishAccessGuard / DishVisibilityGuard 显式校验）；系统模式不过滤。</li>
 * </ul>
 * 实现借用租户插件的 SQL 遍历（JOIN / 子查询 / WITH 都覆盖），仅替换条件表达式。
 */
public class DishVisibilityInnerInterceptor extends TenantLineInnerInterceptor {

    static final String DISH_TABLE = "dish";
    /** 菜谱子表：按 dish_id 归属。 */
    static final Set<String> CHILD_TABLES = Set.of("dish_ingredient", "dish_step", "dish_dict");

    public DishVisibilityInnerInterceptor() {
        super(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long familyId = DishVisibility.effectiveFamilyId();
                return new LongValue(familyId == null ? GuduTenantLineHandler.GUEST_TENANT : familyId);
            }

            @Override
            public String getTenantIdColumn() {
                return DishVisibility.COLUMN;
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (TenantContextHolder.isSystem() || tableName == null) return true;
                String t = normalize(tableName);
                return !DISH_TABLE.equals(t) && !CHILD_TABLES.contains(t);
            }
        });
    }

    static String normalize(String tableName) {
        String t = tableName.replace("`", "").toLowerCase(Locale.ROOT);
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        if (getTenantLineHandler().ignoreTable(table.getName())) return null;
        if (CHILD_TABLES.contains(normalize(table.getName()))) return childExists(table);
        Column col = getAliasColumn(table);
        IsNullExpression isPublic = new IsNullExpression();
        isPublic.setLeftExpression(col);
        Long familyId = DishVisibility.effectiveFamilyId();
        if (familyId == null) return isPublic;
        return new ParenthesedExpressionList<>(new OrExpression(isPublic, new EqualsTo(col, new LongValue(familyId))));
    }

    /** {@code EXISTS (SELECT 1 FROM dish vd WHERE vd.id = <子表>.dish_id AND 可见性)}。 */
    Expression childExists(Table table) {
        String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
        Long familyId = DishVisibility.effectiveFamilyId();
        String visible = familyId == null ? "vd.family_id IS NULL"
                : "(vd.family_id IS NULL OR vd.family_id = " + familyId + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(
                    "EXISTS (SELECT 1 FROM dish vd WHERE vd.id = " + ref + ".dish_id AND " + visible + ")");
        } catch (JSQLParserException e) {
            throw new IllegalStateException("菜谱子表可见性条件构造失败", e);
        }
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        // 写入不改写：归属与权限由 DishService / DishAccessGuard / DishVisibilityGuard 显式校验
    }
}

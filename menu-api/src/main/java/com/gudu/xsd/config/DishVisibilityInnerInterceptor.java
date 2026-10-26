package com.gudu.xsd.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gudu.xsd.common.tenant.DishVisibility;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

import java.util.Locale;

/**
 * 菜谱可见性兜底（design §4.3）：凡经 MyBatis 读取 {@code dish} 表的 SELECT（含跨模块直接用 DishMapper 的存量代码），
 * 自动追加 {@code (dish.family_id IS NULL OR dish.family_id = 当前家庭)}；游客 / 无上下文只看公共菜谱；系统模式不过滤。
 *
 * <p>只改写读：INSERT / UPDATE / DELETE 原样放行，写权限（本家可写、公共菜谱仅平台运营）由 DishService 显式校验。
 * 实现借用租户插件的 SQL 遍历（JOIN / 子查询 / WITH 都覆盖），仅替换条件表达式。
 */
public class DishVisibilityInnerInterceptor extends TenantLineInnerInterceptor {

    static final String DISH_TABLE = "dish";

    public DishVisibilityInnerInterceptor() {
        super(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long familyId = TenantContextHolder.familyIdOrNull();
                return new LongValue(familyId == null ? GuduTenantLineHandler.GUEST_TENANT : familyId);
            }

            @Override
            public String getTenantIdColumn() {
                return DishVisibility.COLUMN;
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (TenantContextHolder.isSystem() || tableName == null) return true;
                String t = tableName.replace("`", "").toLowerCase(Locale.ROOT);
                int dot = t.lastIndexOf('.');
                return !DISH_TABLE.equals(dot >= 0 ? t.substring(dot + 1) : t);
            }
        });
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        if (getTenantLineHandler().ignoreTable(table.getName())) return null;
        Column col = getAliasColumn(table);
        IsNullExpression isPublic = new IsNullExpression();
        isPublic.setLeftExpression(col);
        Long familyId = TenantContextHolder.familyIdOrNull();
        if (familyId == null) return isPublic;
        return new ParenthesedExpressionList<>(new OrExpression(isPublic, new EqualsTo(col, new LongValue(familyId))));
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        // 写不改写：DishService 显式写入 family_id 并校验权限
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        // 同上
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        // 同上
    }
}

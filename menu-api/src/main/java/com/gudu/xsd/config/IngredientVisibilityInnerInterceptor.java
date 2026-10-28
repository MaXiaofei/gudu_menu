package com.gudu.xsd.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gudu.xsd.common.tenant.DishVisibility;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;

import java.util.Locale;

/**
 * 食材可见性兜底（V95）：凡经 MyBatis 访问 {@code ingredient} 表的 SQL（含跨模块直接用 Mapper 的存量代码）一律追加
 * {@code (ingredient.family_id IS NULL OR ingredient.family_id = 当前家庭)}：
 * 公共食材人人可见，家庭私有食材只有本家可见，别家读不到 / 改不动 / 删不掉（按不存在处理，不泄露存在性）。
 * 游客 / 无家庭上下文 / 管理后台登录（scope=admin）只看公共食材；系统模式不过滤（运营收编需要看全部私有食材）。
 * INSERT 不改写：归属由 IngredientCatalogAppService 显式写入。
 * 模式同 {@link DishVisibilityInnerInterceptor}。
 */
public class IngredientVisibilityInnerInterceptor extends TenantLineInnerInterceptor {

    static final String TABLE = "ingredient";

    public IngredientVisibilityInnerInterceptor() {
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
                return !TABLE.equals(normalize(tableName));
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
        Column col = getAliasColumn(table);
        IsNullExpression isPublic = new IsNullExpression();
        isPublic.setLeftExpression(col);
        Long familyId = DishVisibility.effectiveFamilyId();
        if (familyId == null) return isPublic;
        return new ParenthesedExpressionList<>(new OrExpression(isPublic, new EqualsTo(col, new LongValue(familyId))));
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        // 写入不改写：归属由应用服务显式校验写入
    }
}

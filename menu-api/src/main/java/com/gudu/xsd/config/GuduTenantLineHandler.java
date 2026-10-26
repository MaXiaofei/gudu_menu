package com.gudu.xsd.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.common.tenant.TenantTables;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * 租户行处理器（design §4.2）：家庭域表自动追加 {@code family_id = 当前家庭}。
 *
 * <ul>
 *   <li>系统模式（{@link TenantContextHolder#callAsSystem}）：全部表忽略过滤，调用方自带条件；</li>
 *   <li>无上下文（后台任务漏设 runAs）：抛 {@link IllegalStateException}，<b>不放行</b>；</li>
 *   <li>游客（familyId=null）：追加 {@code family_id = -1}，读为空；写由 {@link GuduTenantLineInnerInterceptor} 拒绝。</li>
 * </ul>
 */
public class GuduTenantLineHandler implements TenantLineHandler {

    /** 游客读家庭域表时的占位租户（不存在的家庭）。 */
    static final long GUEST_TENANT = -1L;

    @Override
    public Expression getTenantId() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("访问家庭域表缺少租户上下文（后台任务请用 TenantContextHolder.runAs）");
        }
        return new LongValue(ctx.familyId() == null ? GUEST_TENANT : ctx.familyId());
    }

    @Override
    public String getTenantIdColumn() {
        return TenantTables.COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContextHolder.isSystem()) return true;
        return !TenantTables.isTenantTable(tableName);
    }
}

package com.gudu.xsd.config;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gudu.xsd.common.tenant.TenantAccessDeniedException;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.common.tenant.TenantTables;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 在 MyBatis-Plus 租户插件基础上加一道写保护：游客（无家庭）写家庭域表直接拒绝，
 * 避免以占位租户落脏数据。读仍由 {@link GuduTenantLineHandler} 处理（游客读为空）。
 */
public class GuduTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    public GuduTenantLineInnerInterceptor() {
        super(new GuduTenantLineHandler());
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        guardWrite(insert.getTable());
        super.processInsert(insert, index, sql, obj);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        guardWrite(update.getTable());
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        guardWrite(delete.getTable());
        super.processDelete(delete, index, sql, obj);
    }

    static void guardWrite(Table table) {
        if (TenantContextHolder.isSystem() || table == null || !TenantTables.isTenantTable(table.getName())) return;
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null && ctx.isGuest()) {
            throw new TenantAccessDeniedException("请先登录并选择家庭");
        }
    }
}

package com.gudu.xsd.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 审计字段自动填充：声明了 {@code @TableField(fill = INSERT)} 的 {@code createdBy} 在插入时取当前登录账号
 * （TenantContext.accountId；后台任务 / 游客为 null 则不填）。目前用于 dish.created_by（V89，导出我的数据）。
 */
@Component
public class AuditFieldFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null && ctx.accountId() != null) {
            strictInsertFill(metaObject, "createdBy", Long.class, ctx.accountId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 无更新填充字段
    }
}

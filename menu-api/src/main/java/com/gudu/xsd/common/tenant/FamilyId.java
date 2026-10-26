package com.gudu.xsd.common.tenant;

import com.gudu.xsd.common.domain.DomainException;

/**
 * 家庭标识（共享内核值对象，multi-tenant-family）。家庭 = 数据隔离单元（租户）。
 * 业务上下文只认 FamilyId / {@link TenantContext}，不引用 family 上下文的 Family 聚合。
 */
public record FamilyId(Long value) {

    public FamilyId {
        if (value == null || value <= 0) throw new DomainException("家庭不存在");
    }

    public static FamilyId of(Long value) {
        return new FamilyId(value);
    }
}

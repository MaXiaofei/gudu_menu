package com.gudu.xsd.common.tenant;

import com.gudu.xsd.common.BizException;

/**
 * 家庭访问被拒（403 FAMILY_ACCESS_DENIED）：账号已不是当前家庭成员 / 未选家庭却写家庭数据。
 * 前端据 {@link #REASON} 回到家庭选择。
 */
public class TenantAccessDeniedException extends BizException {

    public static final String REASON = "FAMILY_ACCESS_DENIED";

    public TenantAccessDeniedException(String msg) {
        super(FORBIDDEN, msg);
    }
}

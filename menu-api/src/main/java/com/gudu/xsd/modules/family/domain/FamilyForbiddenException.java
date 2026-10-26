package com.gudu.xsd.modules.family.domain;

import com.gudu.xsd.common.domain.DomainException;

/** 家庭内角色不足（接口层映射为 403；跨家庭访问则在应用层先判为 404，不泄露存在性）。 */
public class FamilyForbiddenException extends DomainException {

    public FamilyForbiddenException(String message) {
        super(message);
    }
}

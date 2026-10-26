package com.gudu.xsd.modules.privacy.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;

/**
 * 账号撤回了某项单独同意（B.2）。成员档案所在上下文（family）同事务消费：删除该账号提供的对应字段——
 * SENSITIVE_HEALTH → 本人档案与其提供的全部健康档案；CHILD_INFO → 其提供的儿童档案的健康信息。
 */
public final class SensitiveConsentWithdrawn extends AbstractDomainEvent {

    private final Long accountId;
    private final ConsentDocType docType;

    public SensitiveConsentWithdrawn(Long accountId, ConsentDocType docType) {
        this.accountId = accountId;
        this.docType = docType;
    }

    public Long accountId() {
        return accountId;
    }

    public ConsentDocType docType() {
        return docType;
    }
}

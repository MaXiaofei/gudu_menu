package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 账号注销时同意记录<b>保留</b>（法定留存的合规证据，只与匿名化后的账号 id 关联）：本步骤不删除任何数据，
 * 只在注销记录里登记保留条数，供后台核对。幂等（只读）。
 */
@Component
@RequiredArgsConstructor
public class ConsentRetainedStep implements AccountErasureStep {

    private final ConsentRepository consents;

    @Override
    public String name() {
        return "同意记录保留（法定留存）";
    }

    @Override
    public String erase(Long accountId) {
        return "保留 " + consents.history(accountId).size() + " 条，仅与匿名账号 id 关联";
    }
}

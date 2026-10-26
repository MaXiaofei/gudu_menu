package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import com.gudu.xsd.modules.privacy.domain.repository.ExportGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 账号注销后续匿名化（隐私政策 §七.6）：作废该账号已签发的「导出我的数据」下载链接凭证。幂等。 */
@Component
@RequiredArgsConstructor
public class ExportLinkErasureStep implements AccountErasureStep {

    private final ExportGuard guard;

    @Override
    public String name() {
        return "导出链接凭证作废";
    }

    @Override
    public String erase(Long accountId) {
        return "作废 " + guard.revokeAll(accountId) + " 个";
    }
}

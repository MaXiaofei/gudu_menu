package com.gudu.xsd.modules.feedback.application;

import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 账号注销后续匿名化（mini-release-compliance F.2）：意见反馈按账号归属、不随家庭保留，
 * 清除其中的联系方式与设备信息（反馈正文保留用于产品改进，已无法关联到个人）。幂等。
 */
@Component
@RequiredArgsConstructor
public class FeedbackAccountErasureStep implements AccountErasureStep {

    private final FeedbackRepository repository;

    @Override
    public String name() {
        return "意见反馈联系方式清除";
    }

    @Override
    public String erase(Long accountId) {
        return "清除 " + repository.anonymizeAccount(accountId) + " 条";
    }
}

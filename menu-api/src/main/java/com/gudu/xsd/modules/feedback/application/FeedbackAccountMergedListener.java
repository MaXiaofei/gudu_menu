package com.gudu.xsd.modules.feedback.application;

import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并（multi-tenant-family E.7）：意见反馈按账号归属，微信账号 W 并入手机号账号 P 后同事务改指 P，
 * 合并后用哪种方式登录都能看到。
 */
@Component
@RequiredArgsConstructor
public class FeedbackAccountMergedListener {

    private final FeedbackRepository feedbackRepository;

    @EventListener
    public void on(AccountMerged e) {
        feedbackRepository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}

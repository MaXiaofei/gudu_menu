package com.gudu.xsd.modules.vision.application;

import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并（multi-tenant-family E.7）：拍照识别任务按账号归属，微信账号 W 并入手机号账号 P 后同事务改指 P，
 * 合并后用哪种方式登录都能看到。
 */
@Component
@RequiredArgsConstructor
public class VisionAccountMergedListener {

    private final VisionJobRepository visionJobRepository;

    @EventListener
    public void on(AccountMerged e) {
        visionJobRepository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}

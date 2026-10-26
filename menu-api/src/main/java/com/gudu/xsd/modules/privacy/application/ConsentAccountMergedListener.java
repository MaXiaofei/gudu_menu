package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并（multi-tenant-family E.7 留给本 change 的监听）：同意记录按账号归属，W 并入 P 后同事务改指 P，
 * 合并后同意状态与历史随账号保留。
 */
@Component
@RequiredArgsConstructor
public class ConsentAccountMergedListener {

    private final ConsentRepository repository;

    @EventListener
    public void on(AccountMerged e) {
        repository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}

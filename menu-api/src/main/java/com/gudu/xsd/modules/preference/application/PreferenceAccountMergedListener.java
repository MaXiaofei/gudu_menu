package com.gudu.xsd.modules.preference.application;

import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.preference.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并：用户偏好（user_preference.member_id = 账号 id）按账号归属，W 并入 P 后同事务改指 P；
 * P 已有偏好则保留 P 的。否则 W 关掉的「个性化推荐」会留在已合并的账号上，合并后失效。幂等。
 */
@Component
@RequiredArgsConstructor
public class PreferenceAccountMergedListener {

    private final UserPreferenceRepository preferences;

    @EventListener
    public void on(AccountMerged e) {
        preferences.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}

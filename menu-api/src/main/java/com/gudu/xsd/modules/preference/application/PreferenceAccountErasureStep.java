package com.gudu.xsd.modules.preference.application;

import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import com.gudu.xsd.modules.preference.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 账号注销后续匿名化（隐私政策 §七.6）：删除该账号的用户偏好（个性化推荐开关）。幂等。 */
@Component
@RequiredArgsConstructor
public class PreferenceAccountErasureStep implements AccountErasureStep {

    private final UserPreferenceRepository preferences;

    @Override
    public String name() {
        return "用户偏好删除";
    }

    @Override
    public String erase(Long accountId) {
        return "删除 " + preferences.deleteByMemberId(accountId) + " 条";
    }
}

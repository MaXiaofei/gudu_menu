package com.gudu.xsd.modules.preference.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.preference.domain.model.UserPreference;
import com.gudu.xsd.modules.preference.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户偏好（A.8）：个性化推荐开关。上下文 API：{@link #personalizedRecommendEnabled(Long)} 供智荐 / 咕嘟首屏判断。
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceAppService {

    private final UserPreferenceRepository repository;

    public PreferenceView get(Long memberId) {
        return view(load(memberId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PreferenceView update(Long memberId, Boolean personalizedRecommend) {
        UserPreference p = load(memberId);
        if (personalizedRecommend == null) throw new BizException("personalizedRecommend 不能为空");
        p.switchPersonalizedRecommend(personalizedRecommend);
        repository.save(p);
        return view(p);
    }

    /** 账号是否开启个性化推荐（未登录 / 无记录按默认开启）。 */
    public boolean personalizedRecommendEnabled(Long memberId) {
        if (memberId == null) return true;
        return load(memberId).personalizedRecommend();
    }

    private UserPreference load(Long memberId) {
        return repository.findByMemberId(memberId).orElseGet(() -> UserPreference.defaults(memberId));
    }

    private static PreferenceView view(UserPreference p) {
        return new PreferenceView(p.personalizedRecommend());
    }
}

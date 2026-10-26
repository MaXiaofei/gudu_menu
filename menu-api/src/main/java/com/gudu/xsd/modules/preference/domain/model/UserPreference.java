package com.gudu.xsd.modules.preference.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

/**
 * 用户偏好（聚合根，键 = 登录账号 member.id）。没存过的账号用 {@link #defaults(Long)}：个性化推荐默认开启。
 * 个性化推荐开关对应个人信息保护法第 24 条「提供不针对其个人特征的选项」。
 */
public final class UserPreference extends AggregateRoot<Long> {

    private final Long memberId;
    private boolean personalizedRecommend;

    private UserPreference(Long memberId, boolean personalizedRecommend) {
        if (memberId == null) throw new DomainException("请先登录");
        this.memberId = memberId;
        this.personalizedRecommend = personalizedRecommend;
    }

    public static UserPreference defaults(Long memberId) {
        return new UserPreference(memberId, true);
    }

    public static UserPreference rehydrate(Long memberId, boolean personalizedRecommend) {
        return new UserPreference(memberId, personalizedRecommend);
    }

    public void switchPersonalizedRecommend(boolean on) {
        this.personalizedRecommend = on;
    }

    @Override
    public Long id() {
        return memberId;
    }

    public Long memberId() {
        return memberId;
    }

    public boolean personalizedRecommend() {
        return personalizedRecommend;
    }
}

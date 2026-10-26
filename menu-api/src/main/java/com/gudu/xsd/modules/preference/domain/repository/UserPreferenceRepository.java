package com.gudu.xsd.modules.preference.domain.repository;

import com.gudu.xsd.modules.preference.domain.model.UserPreference;

import java.util.Optional;

public interface UserPreferenceRepository {

    Optional<UserPreference> findByMemberId(Long memberId);

    /** 不存在则插入，存在则更新。 */
    void save(UserPreference preference);
}

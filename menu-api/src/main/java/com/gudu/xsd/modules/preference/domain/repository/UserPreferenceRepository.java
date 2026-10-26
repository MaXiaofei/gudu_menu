package com.gudu.xsd.modules.preference.domain.repository;

import com.gudu.xsd.modules.preference.domain.model.UserPreference;

import java.util.Optional;

public interface UserPreferenceRepository {

    Optional<UserPreference> findByMemberId(Long memberId);

    /** 注销：删除该账号的偏好（无记录返回 0，幂等）。 */
    int deleteByMemberId(Long memberId);

    /** 不存在则插入，存在则更新。 */
    void save(UserPreference preference);
}

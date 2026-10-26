package com.gudu.xsd.modules.preference.domain.repository;

import com.gudu.xsd.modules.preference.domain.model.UserPreference;

import java.util.Optional;

public interface UserPreferenceRepository {

    Optional<UserPreference> findByMemberId(Long memberId);

    /** 注销：删除该账号的偏好（无记录返回 0，幂等）。 */
    int deleteByMemberId(Long memberId);

    /** 不存在则插入，存在则更新。 */
    void save(UserPreference preference);

    /** 账号合并：偏好 W → P；P 已有偏好则保留 P 的、删 W 的（member_id 是主键）。返回处理条数。 */
    int reassignAccount(Long from, Long to);
}

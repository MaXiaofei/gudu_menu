package com.gudu.xsd.modules.member.domain.repository;

import com.gudu.xsd.modules.member.domain.model.MemberProfile;

import java.util.Optional;

public interface MemberProfileRepository {

    Optional<MemberProfile> findById(Long memberId);

    /** 只写资料字段（name / avatar_url，avatar 可置空）。 */
    void save(MemberProfile profile);
}

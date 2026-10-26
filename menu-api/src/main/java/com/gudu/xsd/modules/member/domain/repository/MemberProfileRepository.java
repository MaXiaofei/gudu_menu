package com.gudu.xsd.modules.member.domain.repository;

import com.gudu.xsd.modules.member.domain.model.MemberProfile;

import java.util.Optional;

public interface MemberProfileRepository {

    Optional<MemberProfile> findById(Long memberId);

    /** 登录账号在当前家庭的关联档案（member 表按当前家庭过滤）。 */
    Optional<MemberProfile> findByAccount(Long accountId);

    /** 只写资料字段（name / avatar_url，avatar 可置空）。 */
    void save(MemberProfile profile);
}

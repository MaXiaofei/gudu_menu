package com.gudu.xsd.modules.member.domain.repository;

import com.gudu.xsd.modules.member.domain.model.MemberProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberProfileRepository {

    Optional<MemberProfile> findById(Long memberId);

    /** 头像地址在给定集合内的成员（违规图片清头像用）。 */
    List<MemberProfile> findByAvatarUrls(Collection<String> avatarUrls);

    /** 只写资料字段（name / avatar_url，avatar 可置空）。 */
    void save(MemberProfile profile);
}

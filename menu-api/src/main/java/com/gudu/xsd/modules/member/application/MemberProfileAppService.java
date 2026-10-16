package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 个人资料（G7）：只能读写自己（memberId 一律来自登录态）。 */
@Service
@RequiredArgsConstructor
public class MemberProfileAppService {

    private final MemberProfileRepository repository;

    public MemberMeView me(Long memberId) {
        return toView(load(memberId));
    }

    /** name / avatarUrl 为 null 表示不改；avatarUrl 空串表示清除。 */
    @Transactional(rollbackFor = Exception.class)
    public MemberMeView update(Long memberId, String name, String avatarUrl) {
        MemberProfile p = load(memberId);
        if (name != null) p.rename(name);
        if (avatarUrl != null) p.changeAvatar(avatarUrl);
        repository.save(p);
        return toView(p);
    }

    private MemberProfile load(Long memberId) {
        return repository.findById(memberId).orElseThrow(() -> new BizException("成员不存在"));
    }

    private static MemberMeView toView(MemberProfile p) {
        return new MemberMeView(p.id(), p.name(), p.avatarUrl(), p.maskedPhone(), p.isAdmin());
    }
}

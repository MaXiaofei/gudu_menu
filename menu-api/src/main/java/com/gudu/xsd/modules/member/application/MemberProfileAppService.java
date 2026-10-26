package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 个人资料（G7）：只能读写自己（memberId 一律来自登录态）。 */
@Service
@RequiredArgsConstructor
public class MemberProfileAppService {

    private final MemberProfileRepository repository;
    private final ContentSecurityAppService contentSecurity;

    public MemberMeView me(Long memberId) {
        return toView(load(memberId));
    }

    /** name / avatarUrl 为 null 表示不改；avatarUrl 空串表示清除。 */
    @Transactional(rollbackFor = Exception.class)
    public MemberMeView update(Long memberId, String name, String avatarUrl) {
        MemberProfile p = load(memberId);
        String oldName = p.name();
        String oldAvatar = p.avatarUrl();
        if (name != null) p.rename(name);
        if (avatarUrl != null) p.changeAvatar(avatarUrl);
        screen(memberId, p, oldName, oldAvatar);
        repository.save(p);
        return toView(p);
    }

    /** 内容安全（A.6）：只检有变化的昵称 / 头像；不通过或检测不可用（fail-closed）抛业务异常，不保存。 */
    private void screen(Long memberId, MemberProfile p, String oldName, String oldAvatar) {
        if (!Objects.equals(p.name(), oldName)) contentSecurity.assertText(memberId, ContentPurpose.NICKNAME, p.name());
        if (p.avatarUrl() != null && !p.avatarUrl().isEmpty() && !Objects.equals(p.avatarUrl(), oldAvatar)) {
            contentSecurity.assertImageUsable(memberId, p.avatarUrl(), ContentPurpose.AVATAR);
        }
    }

    /** accountId = 登录账号；取其在当前家庭的关联档案（multi-tenant-family 起账号 id ≠ 档案 id）。 */
    private MemberProfile load(Long accountId) {
        return repository.findByAccount(accountId).orElseThrow(() -> new BizException("成员不存在"));
    }

    private static MemberMeView toView(MemberProfile p) {
        return new MemberMeView(p.id(), p.name(), p.avatarUrl(), p.maskedPhone(), p.isAdmin());
    }
}

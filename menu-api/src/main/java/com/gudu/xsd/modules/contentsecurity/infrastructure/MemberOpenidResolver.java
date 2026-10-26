package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.gudu.xsd.modules.contentsecurity.domain.port.OpenidResolver;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** openid 目前存在 member.openid（V50）；多家庭把登录身份挪到账号表后改这里即可。走 MemberService，不跨模块碰 Mapper。 */
@Component
public class MemberOpenidResolver implements OpenidResolver {

    private final MemberService memberService;

    public MemberOpenidResolver(MemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public Optional<String> openidOf(Long accountId) {
        if (accountId == null) return Optional.empty();
        Member m = memberService.getById(accountId);
        return Optional.ofNullable(m).map(Member::getOpenid).filter(s -> !s.isBlank());
    }
}

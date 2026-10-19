package com.gudu.xsd.modules.member;

import com.gudu.xsd.modules.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 平台超管判定（member.isAdmin=1）。供 {@link AdminGuardAspect} 与成员接口的字段级权限使用。
 */
@Component
@RequiredArgsConstructor
public class AdminChecker {

    private final MemberMapper memberMapper;

    public boolean isAdmin(Long memberId) {
        if (memberId == null) return false;
        return isAdmin(memberMapper.selectById(memberId));
    }

    public static boolean isAdmin(Member m) {
        return m != null && Integer.valueOf(1).equals(m.getIsAdmin());
    }

    /** 纯成员档案：无登录凭证（无密码、无微信 openid）且非超管——如宝宝、老人。 */
    public static boolean isProfileOnly(Member m) {
        return m != null && m.getPasswordHash() == null && m.getOpenid() == null && !isAdmin(m);
    }
}

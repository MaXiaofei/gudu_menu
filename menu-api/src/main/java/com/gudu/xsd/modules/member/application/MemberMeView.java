package com.gudu.xsd.modules.member.application;

/** GET /member/me：当前登录成员资料（phone 已脱敏）。 */
public record MemberMeView(Long id, String name, String avatarUrl, String phone, boolean isAdmin) {
}

package com.gudu.xsd.modules.member.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.UploadedImageUrl;

/** 个人资料（member 聚合的资料切面）：昵称 + 头像；手机号只读脱敏展示。 */
public final class MemberProfile extends AggregateRoot<Long> {

    public static final int MAX_NAME_LENGTH = 20;

    private final Long id;
    private String name;
    private String avatarUrl;
    private final String phone;
    private final boolean admin;

    private MemberProfile(Long id, String name, String avatarUrl, String phone, boolean admin) {
        if (id == null) throw new DomainException("成员不存在");
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.admin = admin;
    }

    public static MemberProfile rehydrate(Long id, String name, String avatarUrl, String phone, boolean admin) {
        return new MemberProfile(id, name, avatarUrl, phone, admin);
    }

    /** 改昵称：去首尾空白后 1–20 字。 */
    public void rename(String newName) {
        String n = newName == null ? "" : newName.trim();
        if (n.isEmpty() || n.length() > MAX_NAME_LENGTH) throw new DomainException("昵称需 1–20 个字");
        this.name = n;
    }

    /** 换头像：必须是本站上传地址；空串 = 清除头像。 */
    public void changeAvatar(String url) {
        if (url == null || url.isBlank()) {
            this.avatarUrl = null;
            return;
        }
        this.avatarUrl = new UploadedImageUrl(url.trim()).value();
    }

    /** 手机号脱敏：11 位数字 → 138****0001；非手机号（如超管字面量 admin）→ null。 */
    public String maskedPhone() {
        if (phone == null || !phone.matches("^\\d{11}$")) return null;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    @Override
    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public boolean isAdmin() {
        return admin;
    }
}

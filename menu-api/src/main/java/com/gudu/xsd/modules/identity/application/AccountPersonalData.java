package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 导出我的数据（C.2）：账号资料（本人的手机号以明文导出，密码哈希不导出）。 */
@Component
@RequiredArgsConstructor
public class AccountPersonalData implements PersonalDataSection {

    private final AccountRepository accounts;

    @Override
    public String key() {
        return "account";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        return accounts.findById(s.accountId()).map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("nickname", a.nickname());
            m.put("avatarUrl", a.avatarUrl());
            m.put("phone", a.phone() == null ? null : a.phone().value());
            m.put("wechatOpenid", a.wechat() == null ? null : a.wechat().openid());
            m.put("hasPassword", a.passwordHash() != null);
            m.put("platformRole", a.platformRole().name());
            m.put("status", a.status().name());
            m.put("createdAt", a.createdAt());
            return (Object) m;
        }).orElse(null);
    }
}

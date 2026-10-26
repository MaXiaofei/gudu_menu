package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 导出我的数据（C.2）：同意 / 撤回记录（类型、版本、时间、渠道、IP）。 */
@Component
@RequiredArgsConstructor
public class ConsentPersonalData implements PersonalDataSection {

    private final ConsentRepository repository;

    @Override
    public String key() {
        return "consents";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        return repository.history(s.accountId()).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("docType", r.docType().code());
            m.put("docVersion", r.docVersion());
            m.put("granted", r.granted());
            m.put("channel", r.channel());
            m.put("clientIp", r.clientIp());
            m.put("createdAt", r.createdAt());
            return m;
        }).toList();
    }
}

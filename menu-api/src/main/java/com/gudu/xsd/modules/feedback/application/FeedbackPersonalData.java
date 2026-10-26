package com.gudu.xsd.modules.feedback.application;

import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 导出我的数据（C.2）：本账号提交的意见反馈（feedback.member_id = 账号 id）。 */
@Component
@RequiredArgsConstructor
public class FeedbackPersonalData implements PersonalDataSection {

    private final FeedbackRepository repository;

    @Override
    public String key() {
        return "feedback";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        return repository.listByMember(s.accountId()).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id());
            m.put("content", f.content());
            m.put("contact", f.contact());
            m.put("images", f.images());
            m.put("clientInfo", f.clientInfo());
            m.put("status", f.status().name());
            m.put("createTime", f.createTime());
            return m;
        }).toList();
    }
}

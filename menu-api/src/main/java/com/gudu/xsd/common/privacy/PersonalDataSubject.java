package com.gudu.xsd.common.privacy;

import java.util.List;

/**
 * 导出主体：登录账号 + 该账号在各家庭的本人成员档案 id（按档案归属的数据——做菜记录、食记、点评、AI 对话——用后者）。
 */
public record PersonalDataSubject(Long accountId, List<Long> profileIds) {

    public PersonalDataSubject {
        profileIds = profileIds == null ? List.of() : List.copyOf(profileIds);
    }
}

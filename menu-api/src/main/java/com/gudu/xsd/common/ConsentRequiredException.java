package com.gudu.xsd.common;

import java.util.List;

/**
 * 保存敏感个人信息（健康档案 / 不满 14 周岁儿童信息）前缺少单独同意（mini-release-compliance B.2）。
 * 响应 code=428，data.reason=CONSENT_REQUIRED，data.docTypes=缺少的同意类型（sensitive_health / child_info）。
 */
public class ConsentRequiredException extends BizException {

    public static final String REASON = "CONSENT_REQUIRED";

    private final List<String> docTypes;

    public ConsentRequiredException(String msg, List<String> docTypes) {
        super(CONSENT_REQUIRED, msg);
        this.docTypes = List.copyOf(docTypes);
    }

    public List<String> docTypes() {
        return docTypes;
    }
}

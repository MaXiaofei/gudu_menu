package com.gudu.xsd.modules.privacy.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/**
 * 同意记录的文档类型（consent_record.doc_type）。本 change 落地两类「单独同意」（个人信息保护法第 29、31 条）：
 * <ul>
 *   <li>{@link #SENSITIVE_HEALTH}：成员健康档案（身高体重、过敏、特殊饮食人群等）；</li>
 *   <li>{@link #CHILD_INFO}：不满十四周岁儿童信息（含宝宝辅食档案），由监护人同意。</li>
 * </ul>
 * 协议同意（terms / privacy，B.1）合入时在此扩展。
 */
public enum ConsentDocType {
    SENSITIVE_HEALTH("sensitive_health"),
    CHILD_INFO("child_info");

    private final String code;

    ConsentDocType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ConsentDocType of(String code) {
        for (ConsentDocType t : values()) {
            if (t.code.equalsIgnoreCase(code == null ? "" : code.trim())) return t;
        }
        throw new DomainException("不支持的同意类型：" + code);
    }
}

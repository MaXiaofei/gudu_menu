package com.gudu.xsd.modules.privacy.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.util.Arrays;
import java.util.List;

/**
 * 同意记录的文档类型（consent_record.doc_type）。
 * <ul>
 *   <li>单独同意（个人信息保护法第 29、31 条，B.2）：{@link #SENSITIVE_HEALTH} 成员健康档案；
 *       {@link #CHILD_INFO} 不满十四周岁儿童信息（监护人同意）；</li>
 *   <li>协议同意（B.1）：{@link #TERMS} 用户协议、{@link #PRIVACY} 隐私政策——登录页勾选、登录成功后上报。</li>
 * </ul>
 */
public enum ConsentDocType {
    SENSITIVE_HEALTH("sensitive_health", true),
    CHILD_INFO("child_info", true),
    TERMS("terms", false),
    PRIVACY("privacy", false);

    private final String code;
    private final boolean separate;

    ConsentDocType(String code, boolean separate) {
        this.code = code;
        this.separate = separate;
    }

    public String code() {
        return code;
    }

    /** 是否「单独同意」类（敏感信息弹窗）；否则为登录协议同意。 */
    public boolean separate() {
        return separate;
    }

    /** 单独同意类型（B.2 状态接口只返回这些）。 */
    public static List<ConsentDocType> separateTypes() {
        return Arrays.stream(values()).filter(ConsentDocType::separate).toList();
    }

    /** 登录协议类型（B.1）。 */
    public static List<ConsentDocType> legalTypes() {
        return Arrays.stream(values()).filter(t -> !t.separate).toList();
    }

    public static ConsentDocType of(String code) {
        for (ConsentDocType t : values()) {
            if (t.code.equalsIgnoreCase(code == null ? "" : code.trim())) return t;
        }
        throw new DomainException("不支持的同意类型：" + code);
    }
}

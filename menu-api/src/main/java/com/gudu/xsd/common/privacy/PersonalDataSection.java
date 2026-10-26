package com.gudu.xsd.common.privacy;

/**
 * 「导出我的数据」（mini-release-compliance C.2）的一节：各业务上下文实现本接口（Spring Bean），
 * 只导出归属于 {@link PersonalDataSubject} 的本人数据，<b>不得</b>包含其他家庭成员的敏感信息。
 *
 * <p>调用方在系统模式（绕过租户过滤）下调用——导出跨用户所属的全部家庭，实现必须自带归属条件
 * （account_id / 本人档案 id / created_by）。返回值直接序列化为 JSON。
 */
public interface PersonalDataSection {

    /** 导出 JSON 中的字段名（英文 camelCase）。 */
    String key();

    /** 排序（小在前）。 */
    default int order() {
        return 100;
    }

    Object export(PersonalDataSubject subject);
}

package com.gudu.xsd.modules.privacy.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.model.ConsentRecord;
import com.gudu.xsd.modules.privacy.domain.service.SensitiveInfoClassifier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.privacy.domain.model.ConsentDocType.CHILD_INFO;
import static com.gudu.xsd.modules.privacy.domain.model.ConsentDocType.SENSITIVE_HEALTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveInfoClassifierTest {

    @Test
    void 无健康信息_空值_空列表_不需要同意() {
        assertThat(SensitiveInfoClassifier.required(null, null, null)).isEmpty();
        assertThat(SensitiveInfoClassifier.required(Map.of(), " ", "")).isEmpty();
        Map<String, Object> blank = new HashMap<>();
        blank.put("allergies", List.of());
        blank.put("audiences", List.of());
        blank.put("note", "");
        blank.put("height", null);
        blank.put("constraints", Map.of());
        assertThat(SensitiveInfoClassifier.required(blank, null, null)).isEmpty();
    }

    @Test
    void 健康字段_体控目标_活动水平_需要健康档案同意() {
        assertThat(SensitiveInfoClassifier.required(Map.of("height", 170), null, null)).containsExactly(SENSITIVE_HEALTH);
        assertThat(SensitiveInfoClassifier.required(Map.of("allergies", List.of("花生")), null, null))
                .containsExactly(SENSITIVE_HEALTH);
        assertThat(SensitiveInfoClassifier.required(null, "LOSE", null)).containsExactly(SENSITIVE_HEALTH);
        assertThat(SensitiveInfoClassifier.required(null, null, "ACTIVE")).containsExactly(SENSITIVE_HEALTH);
        assertThat(SensitiveInfoClassifier.required(Map.of("age", 35, "audiences", List.of("高血压")), null, null))
                .containsExactly(SENSITIVE_HEALTH);
    }

    @Test
    void 不满14周岁_年龄_月龄_宝宝辅食_另需监护人同意() {
        assertThat(SensitiveInfoClassifier.required(Map.of("age", 13), null, null))
                .containsExactlyInAnyOrder(SENSITIVE_HEALTH, CHILD_INFO);
        assertThat(SensitiveInfoClassifier.required(Map.of("age", "6"), null, null)).contains(CHILD_INFO);
        assertThat(SensitiveInfoClassifier.required(Map.of("ageMonth", 12), null, null)).contains(CHILD_INFO);
        assertThat(SensitiveInfoClassifier.required(Map.of("audiences", List.of("宝宝辅食")), null, null))
                .contains(CHILD_INFO);
        assertThat(SensitiveInfoClassifier.isChild(Map.of("audiences", "宝宝辅食,高血压"))).isTrue();
        assertThat(SensitiveInfoClassifier.isChild(Map.of("age", 14))).isFalse();
        assertThat(SensitiveInfoClassifier.isChild(Map.of("age", 0))).as("0 = 未填").isFalse();
        assertThat(SensitiveInfoClassifier.isChild(Map.of("ageMonth", 168))).isFalse();
        assertThat(SensitiveInfoClassifier.isChild(Map.of("age", "abc"))).isFalse();
        assertThat(SensitiveInfoClassifier.isChild(null)).isFalse();
    }

    @Test
    void 同意类型编码_同意记录校验() {
        assertThat(ConsentDocType.of("sensitive_health")).isEqualTo(SENSITIVE_HEALTH);
        assertThat(ConsentDocType.of(" CHILD_INFO ")).isEqualTo(CHILD_INFO);
        assertThatThrownBy(() -> ConsentDocType.of("cookie")).isInstanceOf(DomainException.class);
        // B.1 协议同意类型：可解析，但不属于单独同意
        assertThat(ConsentDocType.of("privacy").separate()).isFalse();
        assertThat(ConsentDocType.separateTypes()).containsExactly(SENSITIVE_HEALTH, CHILD_INFO);
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 9, 0);
        ConsentRecord r = ConsentRecord.grant(1L, CHILD_INFO, "2026-10-26", "mp-weixin-extra-long-channel", " ", now);
        assertThat(r.granted()).isTrue();
        assertThat(r.channel()).hasSize(16);
        assertThat(r.clientIp()).isNull();
        assertThat(ConsentRecord.withdraw(1L, CHILD_INFO, "v", null, "1.2.3.4", now).granted()).isFalse();
        assertThatThrownBy(() -> ConsentRecord.grant(1L, CHILD_INFO, " ", null, null, now))
                .isInstanceOf(DomainException.class);
    }
}

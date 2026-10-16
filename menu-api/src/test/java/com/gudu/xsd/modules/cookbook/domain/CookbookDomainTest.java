package com.gudu.xsd.modules.cookbook.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.cookbook.domain.model.CookedDishRecord;
import com.gudu.xsd.modules.cookbook.domain.service.UsedMemoFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** cookbook 领域：做菜记录聚合 + 用材 memo 格式（纯 JUnit）。 */
class CookbookDomainTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 16, 19, 0);

    @Test
    void memo_两段_单段_全空() {
        assertThat(UsedMemoFormatter.format(List.of(1L, 2L), List.of(3L))).isEqualTo("用完:1,2;用了一些:3");
        assertThat(UsedMemoFormatter.format(List.of(1L), null)).isEqualTo("用完:1");
        assertThat(UsedMemoFormatter.format(null, List.of(3L))).isEqualTo("用了一些:3");
        assertThat(UsedMemoFormatter.format(List.of(), Arrays.asList((Long) null))).isNull();
        assertThat(UsedMemoFormatter.format(null, null)).isNull();
    }

    @Test
    void 记录_字段与id回填() {
        CookedDishRecord r = CookedDishRecord.cooked("e1", 7L, 1L, null, BigDecimal.ONE, "dish", "用完:1", "少盐", AT);
        assertThat(r.eventId()).isEqualTo("e1");
        assertThat(r.memberId()).isEqualTo(7L);
        assertThat(r.dishId()).isEqualTo(1L);
        assertThat(r.menuId()).isNull();
        assertThat(r.servingFactor()).isEqualTo(BigDecimal.ONE);
        assertThat(r.source()).isEqualTo("dish");
        assertThat(r.memo()).isEqualTo("用完:1");
        assertThat(r.note()).isEqualTo("少盐");
        assertThat(r.cookedAt()).isEqualTo(AT);
        r.identify(9L);
        assertThat(r.id()).isEqualTo(9L);
        assertThatThrownBy(() -> r.identify(10L)).isInstanceOf(DomainException.class);
    }

    @Test
    void 记录_必填校验() {
        assertThatThrownBy(() -> CookedDishRecord.cooked("e", 7L, null, null, null, "dish", null, null, AT))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CookedDishRecord.cooked("e", 7L, 1L, null, null, "dish", null, null, null))
                .isInstanceOf(DomainException.class);
    }
}

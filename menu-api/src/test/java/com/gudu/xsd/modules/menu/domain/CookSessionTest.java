package com.gudu.xsd.modules.menu.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSession;
import com.gudu.xsd.modules.menu.domain.model.CookSource;
import com.gudu.xsd.modules.menu.domain.model.CookedDish;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CookSession 聚合 + DishCookedEvent（纯 JUnit）。 */
class CookSessionTest {

    @Test
    void 单菜直做_默认1份_完成登记DishCookedEvent() {
        CookSession s = CookSession.forDish(7L, 1L, null, List.of(10L), List.of(20L), "  少盐  ");
        assertThat(s.source()).isEqualTo(CookSource.DISH);
        assertThat(s.id()).isNotBlank();
        DishCookedEvent e = s.complete();
        assertThat(s.isCompleted()).isTrue();
        assertThat(e.memberId()).isEqualTo(7L);
        assertThat(e.source()).isEqualTo(CookSource.DISH);
        assertThat(e.menuId()).isNull();
        assertThat(e.note()).isEqualTo("少盐");
        assertThat(e.dishes()).containsExactly(new CookedDish(1L, BigDecimal.ONE));
        assertThat(e.usedUp()).containsExactly(10L);
        assertThat(e.partiallyUsed()).containsExactly(20L);
        assertThat(s.pullDomainEvents()).containsExactly(e);
    }

    @Test
    void 用材去重_同时勾选以用完为准_空值忽略() {
        CookSession s = CookSession.forDish(7L, 1L, new BigDecimal("2"),
                Arrays.asList(10L, 10L, null), Arrays.asList(10L, 20L, 20L, null), null);
        assertThat(s.usedUp()).containsExactly(10L);
        assertThat(s.partiallyUsed()).containsExactly(20L);
        assertThat(s.complete().note()).isNull();
    }

    @Test
    void 份数校验_0与负数与超过99拒绝_小数可() {
        assertThatThrownBy(() -> CookSession.forDish(7L, 1L, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(DomainException.class).hasMessageContaining("份数");
        assertThatThrownBy(() -> CookSession.forDish(7L, 1L, new BigDecimal("-1"), null, null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CookSession.forDish(7L, 1L, new BigDecimal("100"), null, null, null))
                .isInstanceOf(DomainException.class);
        assertThat(CookSession.forDish(7L, 1L, new BigDecimal("0.5"), null, null, null).dishes().get(0)
                .servingFactor()).isEqualByComparingTo("0.5");
    }

    @Test
    void 备注超200字拒绝_空白备注为null() {
        assertThatThrownBy(() -> CookSession.forDish(7L, 1L, null, null, null, "x".repeat(201)))
                .isInstanceOf(DomainException.class).hasMessageContaining("200");
        assertThat(CookSession.forDish(7L, 1L, null, null, null, "x".repeat(200)).complete().note()).hasSize(200);
        assertThat(CookSession.forDish(7L, 1L, null, null, null, "   ").complete().note()).isNull();
    }

    @Test
    void 菜谱id为空拒绝() {
        assertThatThrownBy(() -> CookSession.forDish(7L, null, null, null, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 只能完成一次() {
        CookSession s = CookSession.forDish(7L, 1L, null, null, null, null);
        s.complete();
        assertThatThrownBy(s::complete).isInstanceOf(DomainException.class).hasMessageContaining("做完");
    }

    @Test
    void 整集做_带食集id与多道菜() {
        CookSession s = CookSession.forMenu(7L, 3L,
                List.of(new CookedDish(1L, BigDecimal.ONE), new CookedDish(2L, new BigDecimal("2"))), null, null);
        DishCookedEvent e = s.complete();
        assertThat(e.source()).isEqualTo(CookSource.MENU);
        assertThat(e.menuId()).isEqualTo(3L);
        assertThat(e.dishes()).hasSize(2);
        assertThat(CookSource.MENU.code()).isEqualTo("menu");
        assertThat(CookSource.DISH.code()).isEqualTo("dish");
        assertThat(CookSession.forMenu(7L, 3L, null, null, null).dishes()).isEmpty();
        assertThatThrownBy(() -> CookSession.forMenu(7L, null, List.of(), null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 事件不可变_null集合安全() {
        DishCookedEvent e = new DishCookedEvent(1L, CookSource.DISH, null, null, null, null, null);
        assertThat(e.dishes()).isEmpty();
        assertThat(e.usedUp()).isEmpty();
        assertThat(e.partiallyUsed()).isEmpty();
        assertThatThrownBy(() -> e.usedUp().add(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new CookedDish(null, BigDecimal.ONE)).isInstanceOf(DomainException.class);
    }
}

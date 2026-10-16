package com.gudu.xsd.modules.pantry.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.pantry.domain.event.StockLevelChangedEvent;
import com.gudu.xsd.modules.pantry.domain.model.StockChange;
import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.model.StockLogEntry;
import com.gudu.xsd.modules.pantry.domain.model.StockAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** StockItem 聚合 + StockLevel/StockChange/StockLogEntry 值对象（纯 JUnit，无 Spring）。 */
class StockItemTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(19, 0);

    private StockItem item(StockLevel level, LocalDate bb) {
        return StockItem.rehydrate(1L, 10L, level, bb, null);
    }

    // ---------- StockLevel ----------

    @Test
    void 档位解析_合法与非法() {
        assertThat(StockLevel.of("LOW")).isEqualTo(StockLevel.LOW);
        assertThatThrownBy(() -> StockLevel.of("FULL")).isInstanceOf(DomainException.class)
                .hasMessage("库存档位不合法");
        assertThatThrownBy(() -> StockLevel.of(null)).isInstanceOf(DomainException.class);
        assertThat(StockLevel.parseOrNone(null)).isEqualTo(StockLevel.NONE);
        assertThat(StockLevel.parseOrNone("WEIRD")).isEqualTo(StockLevel.NONE);
        assertThat(StockLevel.parseOrNone("ENOUGH")).isEqualTo(StockLevel.ENOUGH);
    }

    @Test
    void 档位_可用与用了一些降档规则() {
        assertThat(StockLevel.ENOUGH.isAvailable()).isTrue();
        assertThat(StockLevel.LOW.isAvailable()).isTrue();
        assertThat(StockLevel.NONE.isAvailable()).isFalse();
        assertThat(StockLevel.ENOUGH.afterPartialUse()).isEqualTo(StockLevel.LOW);
        assertThat(StockLevel.LOW.afterPartialUse()).isEqualTo(StockLevel.LOW);
        assertThat(StockLevel.NONE.afterPartialUse()).isEqualTo(StockLevel.NONE);
    }

    // ---------- create / rehydrate ----------

    @Test
    void 新建档_登记事件_before为空() {
        StockItem s = StockItem.create(10L, StockLevel.ENOUGH, TODAY.plusDays(3));
        assertThat(s.id()).isNull();
        assertThat(s.level()).isEqualTo(StockLevel.ENOUGH);
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(3));
        assertThat(s.pullDomainEvents()).singleElement().satisfies(e -> {
            StockLevelChangedEvent ev = (StockLevelChangedEvent) e;
            assertThat(ev.ingredientId()).isEqualTo(10L);
            assertThat(ev.before()).isNull();
            assertThat(ev.after()).isEqualTo(StockLevel.ENOUGH);
        });
    }

    @Test
    void 新建档为没有_不记保质期() {
        assertThat(StockItem.create(10L, StockLevel.NONE, TODAY).bestBefore()).isNull();
    }

    @Test
    void 食材id为空_拒绝() {
        assertThatThrownBy(() -> StockItem.create(null, StockLevel.ENOUGH, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 重建_null档位按没有_不登记事件() {
        StockItem s = StockItem.rehydrate(1L, 10L, null, null, null);
        assertThat(s.level()).isEqualTo(StockLevel.NONE);
        assertThat(s.peekDomainEvents()).isEmpty();
    }

    // ---------- restock ----------

    @Test
    void 入库_换新保质期并清开封时间() {
        StockItem s = StockItem.rehydrate(1L, 10L, StockLevel.LOW, TODAY, NOW);
        StockChange c = s.restock(StockLevel.ENOUGH, TODAY.plusDays(7));
        assertThat(c).isEqualTo(new StockChange(StockLevel.LOW, StockLevel.ENOUGH));
        assertThat(c.changed()).isTrue();
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(7));
        assertThat(s.openedAt()).isNull();
        assertThat(s.pullDomainEvents()).hasSize(1);
    }

    @Test
    void 入库为没有_保质期清空_非法档位拒绝() {
        StockItem s = item(StockLevel.ENOUGH, TODAY);
        s.restock(StockLevel.NONE, TODAY.plusDays(2));
        assertThat(s.bestBefore()).isNull();
        assertThatThrownBy(() -> s.restock(null, null)).isInstanceOf(DomainException.class);
    }

    @Test
    void 同档位入库_不登记事件() {
        StockItem s = item(StockLevel.ENOUGH, null);
        assertThat(s.restock(StockLevel.ENOUGH, TODAY).changed()).isFalse();
        assertThat(s.peekDomainEvents()).isEmpty();
    }

    // ---------- adjustLevel / changeBestBefore ----------

    @Test
    void 手动修正_保质期不动_修正为没有则清空() {
        StockItem s = item(StockLevel.ENOUGH, TODAY.plusDays(2));
        s.adjustLevel(StockLevel.LOW);
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(2));
        s.adjustLevel(StockLevel.NONE);
        assertThat(s.bestBefore()).isNull();
        assertThatThrownBy(() -> s.adjustLevel(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void 改保质期_没有档位时忽略() {
        StockItem s = item(StockLevel.LOW, null);
        s.changeBestBefore(TODAY.plusDays(1));
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(1));
        s.changeBestBefore(null);
        assertThat(s.bestBefore()).isNull();
        StockItem none = item(StockLevel.NONE, null);
        none.changeBestBefore(TODAY);
        assertThat(none.bestBefore()).isNull();
    }

    // ---------- 做菜回写 ----------

    @Test
    void 用完了_变没有并清保质期() {
        StockItem s = StockItem.rehydrate(1L, 10L, StockLevel.ENOUGH, TODAY, NOW);
        StockChange c = s.markUsedUp();
        assertThat(c).isEqualTo(new StockChange(StockLevel.ENOUGH, StockLevel.NONE));
        assertThat(s.level()).isEqualTo(StockLevel.NONE);
        assertThat(s.bestBefore()).isNull();
        assertThat(s.openedAt()).isNull();
    }

    @Test
    void 用了一些_充足降为快用完并记开封时间() {
        StockItem s = item(StockLevel.ENOUGH, TODAY.plusDays(3));
        StockChange c = s.markPartiallyUsed(NOW);
        assertThat(c.after()).isEqualTo(StockLevel.LOW);
        assertThat(s.openedAt()).isEqualTo(NOW);
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(3));
        // 再用一些：LOW 不再降、开封时间不覆盖
        StockChange again = s.markPartiallyUsed(NOW.plusDays(1));
        assertThat(again.changed()).isFalse();
        assertThat(s.openedAt()).isEqualTo(NOW);
    }

    @Test
    void 用了一些_没有档位不动也不记开封() {
        StockItem s = item(StockLevel.NONE, null);
        assertThat(s.markPartiallyUsed(NOW).changed()).isFalse();
        assertThat(s.openedAt()).isNull();
    }

    // ---------- 保质期查询 ----------

    @Test
    void 剩余天数_今天到期为0_过期为负_未记为null() {
        assertThat(item(StockLevel.ENOUGH, TODAY).daysLeft(TODAY)).isZero();
        assertThat(item(StockLevel.ENOUGH, TODAY.plusDays(2)).daysLeft(TODAY)).isEqualTo(2);
        assertThat(item(StockLevel.ENOUGH, TODAY.minusDays(1)).daysLeft(TODAY)).isEqualTo(-1);
        assertThat(item(StockLevel.ENOUGH, null).daysLeft(TODAY)).isNull();
        assertThat(StockItem.daysLeft(TODAY, null)).isNull();
    }

    @Test
    void 临期判定_含已过期_没有档位或无保质期不算() {
        assertThat(item(StockLevel.ENOUGH, TODAY.plusDays(3)).isExpiringWithin(3, TODAY)).isTrue();
        assertThat(item(StockLevel.ENOUGH, TODAY.plusDays(4)).isExpiringWithin(3, TODAY)).isFalse();
        assertThat(item(StockLevel.LOW, TODAY.minusDays(2)).isExpiringWithin(3, TODAY)).isTrue();
        assertThat(item(StockLevel.NONE, TODAY).isExpiringWithin(3, TODAY)).isFalse();
        assertThat(item(StockLevel.ENOUGH, null).isExpiringWithin(3, TODAY)).isFalse();
    }

    @Test
    void 回填id只允许一次() {
        StockItem s = StockItem.create(10L, StockLevel.LOW, null);
        s.identify(5L);
        s.identify(5L);
        assertThat(s.id()).isEqualTo(5L);
        assertThatThrownBy(() -> s.identify(6L)).isInstanceOf(DomainException.class);
    }

    @Test
    void 流水条目_必填校验与工厂() {
        StockLogEntry e = StockLogEntry.of(10L, StockAction.COOK, new StockChange(StockLevel.LOW, StockLevel.NONE), "n");
        assertThat(e.before()).isEqualTo(StockLevel.LOW);
        assertThat(e.after()).isEqualTo(StockLevel.NONE);
        assertThat(e.refId()).isNull();
        assertThat(StockAction.COOK_PARTIAL.code()).isEqualTo("cook_partial");
        assertThatThrownBy(() -> new StockLogEntry(null, StockAction.MANUAL, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}

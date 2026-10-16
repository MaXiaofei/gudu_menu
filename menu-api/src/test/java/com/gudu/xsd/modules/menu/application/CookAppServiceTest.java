package com.gudu.xsd.modules.menu.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.menu.CookMaterialsVO;
import com.gudu.xsd.modules.menu.CookService;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 单菜直做应用服务：发布 DishCookedEvent(source=DISH)，回查记录 id。 */
class CookAppServiceTest {

    DishService dishService;
    CookService cookService;
    CookingRecordAppService records;
    List<DomainEvent> published;
    CookAppService svc;

    @BeforeEach
    void setUp() {
        dishService = mock(DishService.class);
        cookService = mock(CookService.class);
        records = mock(CookingRecordAppService.class);
        published = new ArrayList<>();
        DomainEventPublisher pub = published::add;
        svc = new CookAppService(dishService, cookService, records, pub);
    }

    @Test
    void 做菜_发布事件并返回记录id() {
        Dish d = new Dish();
        d.setId(1L);
        when(dishService.getById(1L)).thenReturn(d);
        when(records.recordIdsOf(any())).thenReturn(List.of(55L));

        CookDishResult r = svc.cookDish(1L, 7L, new BigDecimal("2"), List.of(10L), List.of(20L), "好吃");

        assertThat(r).isEqualTo(new CookDishResult(55L, 1L));
        assertThat(published).singleElement().isInstanceOf(DishCookedEvent.class);
        DishCookedEvent e = (DishCookedEvent) published.get(0);
        assertThat(e.source()).isEqualTo(CookSource.DISH);
        assertThat(e.memberId()).isEqualTo(7L);
        assertThat(e.dishes().get(0).servingFactor()).isEqualByComparingTo("2");
        verify(records).recordIdsOf(e.eventId());
    }

    @Test
    void 菜谱不存在_不发事件() {
        assertThatThrownBy(() -> svc.cookDish(9L, 7L, null, null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("菜谱不存在");
        assertThatThrownBy(() -> svc.cookDish(null, 7L, null, null, null, null)).isInstanceOf(BizException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void 领域校验失败_不发事件() {
        when(dishService.getById(1L)).thenReturn(new Dish());
        assertThatThrownBy(() -> svc.cookDish(1L, 7L, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(DomainException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void 记录未写入_抛异常回滚() {
        when(dishService.getById(1L)).thenReturn(new Dish());
        when(records.recordIdsOf(any())).thenReturn(List.of());
        assertThatThrownBy(() -> svc.cookDish(1L, 7L, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 弹窗数据委托CookService() {
        CookMaterialsVO vo = new CookMaterialsVO(null, List.of());
        when(cookService.dishMaterials(1L, BigDecimal.ONE)).thenReturn(vo);
        assertThat(svc.dishMaterials(1L, BigDecimal.ONE)).isSameAs(vo);
    }
}

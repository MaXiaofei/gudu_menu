package com.gudu.xsd.modules.pantry;

import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.mapper.IngredientStockMapper;
import com.gudu.xsd.modules.pantry.mapper.StockLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存档位测试（V42 手动 3 档版）：setLevel/useUp/partialUse/manualAdd/grouped/itemDetail/levelMap。
 * 范式：new PantryService(mock IngredientMapper) + setStockMapper/setStockLogMapper 注入 mock。
 */
class PantryServiceTest {

    private IngredientMapper ingredientMapper;
    private IngredientStockMapper stockMapper;
    private StockLogMapper stockLogMapper;
    private PantryService svc;

    @BeforeEach
    void setup() {
        ingredientMapper = mock(IngredientMapper.class);
        stockMapper = mock(IngredientStockMapper.class);
        stockLogMapper = mock(StockLogMapper.class);
        svc = new PantryService(ingredientMapper);
        svc.setStockMapper(stockMapper);
        svc.setStockLogMapper(stockLogMapper);
    }

    private IngredientStock stock(long id, String level) {
        IngredientStock s = new IngredientStock();
        s.setId(id);
        s.setIngredientId(id);
        s.setLevel(level);
        return s;
    }

    private Ingredient ing(long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        return i;
    }

    // ===================== setLevel =====================

    @Test
    void setLevel_没建档_插入并写流水() {
        when(stockMapper.selectOne(any())).thenReturn(null);

        svc.setLevel(10L, IngredientStock.LEVEL_ENOUGH, StockLog.ACTION_PURCHASE, null, null);

        ArgumentCaptor<IngredientStock> cap = ArgumentCaptor.forClass(IngredientStock.class);
        verify(stockMapper).insert(cap.capture());
        assertThat(cap.getValue().getIngredientId()).isEqualTo(10L);
        assertThat(cap.getValue().getLevel()).isEqualTo(IngredientStock.LEVEL_ENOUGH);
        ArgumentCaptor<StockLog> logCap = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getAction()).isEqualTo(StockLog.ACTION_PURCHASE);
        assertThat(logCap.getValue().getBeforeLevel()).isNull(); // 新建档无前值
        assertThat(logCap.getValue().getAfterLevel()).isEqualTo(IngredientStock.LEVEL_ENOUGH);
    }

    @Test
    void setLevel_已建档_更新档位并写流水() {
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_LOW));

        svc.setLevel(10L, IngredientStock.LEVEL_NONE, StockLog.ACTION_MANUAL, "用完了", null);

        ArgumentCaptor<IngredientStock> cap = ArgumentCaptor.forClass(IngredientStock.class);
        verify(stockMapper).updateById(cap.capture());
        assertThat(cap.getValue().getLevel()).isEqualTo(IngredientStock.LEVEL_NONE);
        ArgumentCaptor<StockLog> logCap = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getNote()).isEqualTo("用完了");
        assertThat(logCap.getValue().getBeforeLevel()).isEqualTo(IngredientStock.LEVEL_LOW);
        assertThat(logCap.getValue().getAfterLevel()).isEqualTo(IngredientStock.LEVEL_NONE);
    }

    @Test
    void setLevel_非法档位_抛异常() {
        assertThatThrownBy(() -> svc.setLevel(10L, "MEGA", StockLog.ACTION_MANUAL, null, null))
                .hasMessageContaining("档位");
        verify(stockMapper, never()).insert(any());
    }

    @Test
    void setLevel_食材id为空_抛异常() {
        assertThatThrownBy(() -> svc.setLevel(null, IngredientStock.LEVEL_ENOUGH, StockLog.ACTION_MANUAL, null, null))
                .hasMessageContaining("食材");
    }

    // ===================== useUp / partialUse =====================

    @Test
    void useUp_直接设为没有() {
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_ENOUGH));

        svc.useUp(10L, StockLog.ACTION_COOK, null);

        ArgumentCaptor<IngredientStock> cap = ArgumentCaptor.forClass(IngredientStock.class);
        verify(stockMapper).updateById(cap.capture());
        assertThat(cap.getValue().getLevel()).isEqualTo(IngredientStock.LEVEL_NONE);
    }

    @Test
    void partialUse_充足降为快用完() {
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_ENOUGH));

        svc.partialUse(10L, StockLog.ACTION_COOK_PARTIAL, null);

        ArgumentCaptor<IngredientStock> cap = ArgumentCaptor.forClass(IngredientStock.class);
        verify(stockMapper).updateById(cap.capture());
        assertThat(cap.getValue().getLevel()).isEqualTo(IngredientStock.LEVEL_LOW);
        ArgumentCaptor<StockLog> logCap = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getBeforeLevel()).isEqualTo(IngredientStock.LEVEL_ENOUGH);
        assertThat(logCap.getValue().getAfterLevel()).isEqualTo(IngredientStock.LEVEL_LOW);
    }

    @Test
    void partialUse_快用完不再降级() {
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_LOW));

        svc.partialUse(10L, StockLog.ACTION_COOK_PARTIAL, null);

        verify(stockMapper, never()).updateById(any());
        verify(stockLogMapper, never()).insert(any());
    }

    @Test
    void partialUse_没有不再降级() {
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_NONE));

        svc.partialUse(10L, StockLog.ACTION_COOK_PARTIAL, null);

        verify(stockMapper, never()).updateById(any());
        verify(stockLogMapper, never()).insert(any());
    }

    @Test
    void partialUse_没建档的食材不动() {
        when(stockMapper.selectOne(any())).thenReturn(null);

        svc.partialUse(10L, StockLog.ACTION_COOK_PARTIAL, null);

        verify(stockMapper, never()).insert(any());
        verify(stockMapper, never()).updateById(any());
        verify(stockLogMapper, never()).insert(any());
    }

    // ===================== manualAdd =====================

    @Test
    void manualAdd_按名匹配已有食材_默认设为充足() {
        when(ingredientMapper.selectList(any())).thenReturn(List.of(ing(10L, "苹果")));
        when(stockMapper.selectOne(any())).thenReturn(null);

        svc.manualAdd(null, "苹果", null, "朋友送");

        ArgumentCaptor<IngredientStock> cap = ArgumentCaptor.forClass(IngredientStock.class);
        verify(stockMapper).insert(cap.capture());
        assertThat(cap.getValue().getIngredientId()).isEqualTo(10L);
        assertThat(cap.getValue().getLevel()).isEqualTo(IngredientStock.LEVEL_ENOUGH);
        ArgumentCaptor<StockLog> logCap = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getNote()).isEqualTo("朋友送");
    }

    @Test
    void manualAdd_未匹配_新建食材并设为指定档位() {
        when(ingredientMapper.selectList(any())).thenReturn(List.of());
        when(ingredientMapper.insert(any())).thenAnswer(inv -> {
            ((Ingredient) inv.getArgument(0)).setId(55L);
            return 1;
        });
        when(stockMapper.selectOne(any())).thenReturn(null);

        svc.manualAdd(null, "山竹", IngredientStock.LEVEL_LOW, "赠品");

        ArgumentCaptor<Ingredient> ingCap = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredientMapper).insert(ingCap.capture());
        assertThat(ingCap.getValue().getName()).isEqualTo("山竹");
        ArgumentCaptor<IngredientStock> cap = ArgumentCaptor.forClass(IngredientStock.class);
        verify(stockMapper).insert(cap.capture());
        assertThat(cap.getValue().getIngredientId()).isEqualTo(55L);
        assertThat(cap.getValue().getLevel()).isEqualTo(IngredientStock.LEVEL_LOW);
    }

    @Test
    void manualAdd_id和名称都空_抛异常() {
        assertThatThrownBy(() -> svc.manualAdd(null, "  ", null, null))
                .hasMessageContaining("至少填一项");
    }

    // ===================== removeLevel（撤回新建档，B3） =====================

    @Test
    void removeLevel_删除档位并记流水_after为空() {
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_ENOUGH));

        svc.removeLevel(10L, StockLog.ACTION_UNDO, null, 5L);

        verify(stockMapper).deleteById((java.io.Serializable) 10L);
        ArgumentCaptor<StockLog> logCap = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getBeforeLevel()).isEqualTo(IngredientStock.LEVEL_ENOUGH);
        assertThat(logCap.getValue().getAfterLevel()).isNull();
        assertThat(logCap.getValue().getRefId()).isEqualTo(5L);
    }

    @Test
    void removeLevel_没建档_无操作() {
        when(stockMapper.selectOne(any())).thenReturn(null);

        svc.removeLevel(10L, StockLog.ACTION_UNDO, null, 5L);

        verify(stockMapper, never()).deleteById((java.io.Serializable) any());
        verify(stockLogMapper, never()).insert(any());
    }

    // ===================== grouped / itemDetail / levelMap =====================

    @Test
    void grouped_按档位分组统计并排序() {
        when(stockMapper.selectList(any())).thenReturn(List.of(
                stock(10L, IngredientStock.LEVEL_ENOUGH),
                stock(11L, IngredientStock.LEVEL_NONE),
                stock(12L, IngredientStock.LEVEL_LOW),
                stock(13L, IngredientStock.LEVEL_NONE)));
        when(ingredientMapper.selectList(any())).thenReturn(List.of(
                ing(10L, "大米"), ing(11L, "葱"), ing(12L, "油"), ing(13L, "鸡蛋")));
        when(stockLogMapper.selectList(any())).thenReturn(List.of());

        PantryGroupedVO vo = svc.grouped();

        assertThat(vo.getSummary().getEnough()).isEqualTo(1);
        assertThat(vo.getSummary().getLow()).isEqualTo(1);
        assertThat(vo.getSummary().getNone()).isEqualTo(2);
        assertThat(vo.getItems()).extracting(PantryGroupedVO.Item::getLevel)
                .containsExactly(IngredientStock.LEVEL_NONE, IngredientStock.LEVEL_NONE,
                        IngredientStock.LEVEL_LOW, IngredientStock.LEVEL_ENOUGH);
    }

    @Test
    void itemDetail_返回档位和流水() {
        when(ingredientMapper.selectById(10L)).thenReturn(ing(10L, "鸡蛋"));
        when(stockMapper.selectOne(any())).thenReturn(stock(10L, IngredientStock.LEVEL_LOW));
        when(stockLogMapper.selectList(any())).thenReturn(List.of());

        PantryItemDetailVO vo = svc.itemDetail(10L);

        assertThat(vo.getIngredientName()).isEqualTo("鸡蛋");
        assertThat(vo.getLevel()).isEqualTo(IngredientStock.LEVEL_LOW);
        assertThat(vo.getChanges()).isEmpty();
    }

    @Test
    void itemDetail_没建档_档位按没有返回() {
        when(ingredientMapper.selectById(10L)).thenReturn(ing(10L, "鸡蛋"));
        when(stockMapper.selectOne(any())).thenReturn(null);
        when(stockLogMapper.selectList(any())).thenReturn(List.of());

        PantryItemDetailVO vo = svc.itemDetail(10L);

        assertThat(vo.getLevel()).isEqualTo(IngredientStock.LEVEL_NONE);
    }

    @Test
    void levelMap_批量返回食材档位() {
        when(stockMapper.selectList(any())).thenReturn(List.of(
                stock(10L, IngredientStock.LEVEL_ENOUGH),
                stock(11L, IngredientStock.LEVEL_NONE)));

        var map = svc.levelMap(List.of(10L, 11L, 99L));

        assertThat(map).containsEntry(10L, IngredientStock.LEVEL_ENOUGH)
                .containsEntry(11L, IngredientStock.LEVEL_NONE)
                .doesNotContainKey(99L);
    }

    // ===================== 保留：临期判定（通知调度用） =====================

    @Test
    void 临期判定_闭区间内算临期() {
        LocalDate today = LocalDate.of(2026, 6, 20);
        assertThat(svc.isExpiring(LocalDate.of(2026, 6, 22), today, 3)).isTrue();
        assertThat(svc.isExpiring(LocalDate.of(2026, 6, 25), today, 3)).isFalse();
        assertThat(svc.isExpiring(LocalDate.of(2026, 6, 19), today, 3)).isFalse();
        assertThat(svc.isExpiring(null, today, 3)).isFalse();
        assertThat(svc.isExpiring(today, today, 3)).isTrue();
        assertThat(svc.isExpiring(today.plusDays(3), today, 3)).isTrue();
    }
}

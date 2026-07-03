package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** deductByIngredient 测试：spy PantryService 桩 list/getById/updateById，不碰真实 DB。 */
class PantryDeductByIngredientTest {

    private Pantry batch(long id, long ingId, String grams, String amount) {
        Pantry p = new Pantry();
        p.setId(id);
        p.setIngredientId(ingId);
        p.setGrams(new BigDecimal(grams));
        p.setAmount(new BigDecimal(amount));
        return p;
    }

    @Test
    void 够_单批次_扣后grams和amount同步缩() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        Pantry p = batch(1, 10, "100", "2");
        doReturn(List.of(p)).when(svc).list(any(Wrapper.class));
        doReturn(p).when(svc).getById(1L);
        doReturn(true).when(svc).updateById(any(Pantry.class));

        PantryService.DeductResult r = svc.deductByIngredient(10L, new BigDecimal("30"));

        assertThat(r.deductedGrams()).isEqualByComparingTo("30");
        assertThat(r.shortageGrams()).isEqualByComparingTo("0");
        verify(svc).updateById(argThat(x ->
                ((Pantry) x).getGrams().compareTo(new BigDecimal("70")) == 0
                && ((Pantry) x).getAmount().compareTo(new BigDecimal("1.40")) == 0));
    }

    @Test
    void 不够_全扣到0_欠量返回() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        Pantry p = batch(1, 10, "30", "1");
        doReturn(List.of(p)).when(svc).list(any(Wrapper.class));
        doReturn(p).when(svc).getById(1L);
        doReturn(true).when(svc).updateById(any(Pantry.class));

        PantryService.DeductResult r = svc.deductByIngredient(10L, new BigDecimal("100"));

        assertThat(r.deductedGrams()).isEqualByComparingTo("30");
        assertThat(r.shortageGrams()).isEqualByComparingTo("70");
        verify(svc).updateById(argThat(x ->
                ((Pantry) x).getGrams().compareTo(new BigDecimal("0")) == 0));
    }

    @Test
    void 多批次_FIFO_按查询返回顺序扣() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        Pantry a = batch(1, 10, "40", "1");
        Pantry b = batch(2, 10, "60", "2");
        doReturn(List.of(a, b)).when(svc).list(any(Wrapper.class));   // 已按 FIFO 排序
        doReturn(a).when(svc).getById(1L);
        doReturn(b).when(svc).getById(2L);
        doReturn(true).when(svc).updateById(any(Pantry.class));

        PantryService.DeductResult r = svc.deductByIngredient(10L, new BigDecimal("70"));

        assertThat(r.shortageGrams()).isEqualByComparingTo("0");
        assertThat(r.batches()).hasSize(2);
        verify(svc, times(2)).updateById(any(Pantry.class));
    }

    @Test
    void 需求为零_不查不扣() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        PantryService.DeductResult r = svc.deductByIngredient(10L, BigDecimal.ZERO);
        assertThat(r.deductedGrams()).isEqualByComparingTo("0");
        verify(svc, never()).list(any(Wrapper.class));
        verify(svc, never()).updateById(any(Pantry.class));
    }
}

package com.gudu.xsd.modules.nutrition.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientController;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.application.IngredientCatalogAppService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** PUT /ingredient 请求体形状识别：扁平 Ingredient（小程序）与 {ingredient, nutritions}（管理后台）。 */
class IngredientPutShapeTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final IngredientCatalogAppService catalog = mock(IngredientCatalogAppService.class);
    private final IngredientController ctl =
            new IngredientController(mock(IngredientService.class), mock(CatalogWriteGuard.class), catalog, mapper);

    @Test
    void 扁平形状_走原update_不碰营养() throws Exception {
        ctl.update(mapper.readTree("{\"id\":8,\"name\":\"家传酱\",\"edible\":2}"));

        ArgumentCaptor<Ingredient> cap = ArgumentCaptor.forClass(Ingredient.class);
        verify(catalog).update(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo(8L);
        assertThat(cap.getValue().getName()).isEqualTo("家传酱");
        assertThat(cap.getValue().getEdible()).isEqualTo(2);
        verify(catalog, never()).updateWithNutrition(any(), any());
    }

    @Test
    void DTO形状_走updateWithNutrition_带营养() throws Exception {
        ctl.update(mapper.readTree("{\"ingredient\":{\"id\":3,\"name\":\"西红柿\",\"purchaseCategoryId\":5},"
                + "\"nutritions\":[{\"metricId\":1,\"value\":18},{\"metricId\":2,\"value\":0.9}]}"));

        ArgumentCaptor<Ingredient> ing = ArgumentCaptor.forClass(Ingredient.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IngredientNutrition>> nuts = ArgumentCaptor.forClass(List.class);
        verify(catalog).updateWithNutrition(ing.capture(), nuts.capture());
        assertThat(ing.getValue().getId()).isEqualTo(3L);
        assertThat(ing.getValue().getName()).isEqualTo("西红柿");
        assertThat(nuts.getValue()).hasSize(2);
        assertThat(nuts.getValue().get(1).getMetricId()).isEqualTo(2L);
        verify(catalog, never()).update(any());
    }

    @Test
    void DTO形状_无营养字段也走DTO分支() throws Exception {
        ctl.update(mapper.readTree("{\"ingredient\":{\"id\":3,\"name\":\"西红柿\"}}"));
        verify(catalog).updateWithNutrition(any(Ingredient.class), isNull());
    }

    @Test
    void 空请求体被拒() {
        assertThatThrownBy(() -> ctl.update(mapper.nullNode())).isInstanceOf(BizException.class);
    }
}

package com.yanhuo.xsd.modules.ai;

import com.yanhuo.xsd.common.BizException;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import com.yanhuo.xsd.modules.ai.impl.MockAiClient;
import com.yanhuo.xsd.modules.nutrition.IngredientNutrition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MockAiClient 营养补全纯规则测试（TDD 先红后绿）。
 * 覆盖：关键词命中表 / 分类兜底 / 无匹配抛 BizException。
 */
class MockAiClientTest {

    private final MockAiClient c = new MockAiClient();

    /** metricId -> value。 */
    private static Map<Long, BigDecimal> toMap(NutritionFillResponse r) {
        return r.nutrition().stream()
                .collect(Collectors.toMap(IngredientNutrition::getMetricId, IngredientNutrition::getValue));
    }

    @Test
    void 关键词命中_番茄() {
        var r = c.fillNutrition(new NutritionFillRequest("番茄", null));
        assertThat(r.source()).isEqualTo("mock");
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("19");   // calorie
        assertThat(m.get(2L)).isEqualByComparingTo("0.9");  // protein
        assertThat(m.get(6L)).isEqualByComparingTo("30");   // gi
    }

    @Test
    void 关键词命中_牛肉() {
        var r = c.fillNutrition(new NutritionFillRequest("牛肉", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("125");
        assertThat(m.get(2L)).isEqualByComparingTo("20.2");
    }

    @Test
    void 关键词命中_米饭() {
        var r = c.fillNutrition(new NutritionFillRequest("米饭", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("116");
        assertThat(m.get(6L)).isEqualByComparingTo("83");
    }

    @Test
    void 关键词命中_补全六项指标() {
        var r = c.fillNutrition(new NutritionFillRequest("鸡蛋", null));
        // 必须 6 项全有（1cal/2protein/3fat/4carb/5sugar/6gi）
        assertThat(r.nutrition()).hasSize(6);
        var m = toMap(r);
        assertThat(m.keySet()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void 分类兜底_含肉走蛋白模板() {
        // 「鸭肉」不在关键词表 → 兜底命中「肉」走蛋白类模板 140/20/6/1/0/0
        var r = c.fillNutrition(new NutritionFillRequest("鸭肉", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("140");
        assertThat(m.get(2L)).isEqualByComparingTo("20");
    }

    @Test
    void 分类兜底_含奶走奶类模板() {
        var r = c.fillNutrition(new NutritionFillRequest("酸奶", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("54");
        assertThat(m.get(6L)).isEqualByComparingTo("27");
    }

    @Test
    void 分类兜底_含菜走蔬菜模板() {
        var r = c.fillNutrition(new NutritionFillRequest("茄子", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("25");
        assertThat(m.get(2L)).isEqualByComparingTo("1.5");
    }

    @Test
    void 分类兜底_含油走油脂模板() {
        var r = c.fillNutrition(new NutritionFillRequest("花生油", null));
        var m = toMap(r);
        assertThat(m.get(1L)).isEqualByComparingTo("899");
        assertThat(m.get(3L)).isEqualByComparingTo("99");
    }

    @Test
    void 无匹配_抛BizException() {
        assertThatThrownBy(() -> c.fillNutrition(new NutritionFillRequest("qqq无意义zzz", null)))
                .isInstanceOf(BizException.class);
    }
}

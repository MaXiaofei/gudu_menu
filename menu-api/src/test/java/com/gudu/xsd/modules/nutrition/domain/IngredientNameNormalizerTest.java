package com.gudu.xsd.modules.nutrition.domain;

import com.gudu.xsd.modules.nutrition.domain.service.IngredientNameNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngredientNameNormalizerTest {

    @Test
    void 去首尾空格和全角空格() {
        assertThat(IngredientNameNormalizer.normalize("  土豆 ")).isEqualTo("土豆");
        assertThat(IngredientNameNormalizer.normalize("　土豆　")).isEqualTo("土豆");
    }

    @Test
    void 全角括号转半角_括号内容保留() {
        assertThat(IngredientNameNormalizer.normalize("猪肉（瘦）")).isEqualTo("猪肉(瘦)");
        assertThat(IngredientNameNormalizer.normalize("粉丝(干)")).isEqualTo("粉丝(干)");
    }

    @Test
    void 多余空白折叠为一个() {
        assertThat(IngredientNameNormalizer.normalize("小   米\t辣")).isEqualTo("小 米 辣");
    }

    @Test
    void null返回空串() {
        assertThat(IngredientNameNormalizer.normalize(null)).isEmpty();
        assertThat(IngredientNameNormalizer.normalize("   ")).isEmpty();
    }
}

package com.gudu.xsd.modules.vision;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.vision.domain.model.ConfidenceLevel;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.model.VisionJobStatus;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.service.ChineseQuantityParser;
import com.gudu.xsd.modules.vision.domain.service.ConfidencePolicy;
import com.gudu.xsd.modules.vision.domain.service.IngredientNameMatcher;
import com.gudu.xsd.modules.vision.domain.service.VisionPrompts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisionDomainTest {

    @Test
    void 置信度分级_八成以上自动_五成到八成待确认_更低丢弃() {
        assertThat(ConfidencePolicy.classify(0.8)).contains(ConfidenceLevel.AUTO);
        assertThat(ConfidencePolicy.classify(0.98)).contains(ConfidenceLevel.AUTO);
        assertThat(ConfidencePolicy.classify(0.79)).contains(ConfidenceLevel.CONFIRM);
        assertThat(ConfidencePolicy.classify(0.5)).contains(ConfidenceLevel.CONFIRM);
        assertThat(ConfidencePolicy.classify(0.49)).isEmpty();
        assertThat(ConfidencePolicy.classify(Double.NaN)).isEmpty();
        // 百分制容错
        assertThat(ConfidencePolicy.classify(92)).contains(ConfidenceLevel.AUTO);
        assertThat(ConfidencePolicy.normalize(150)).isEqualTo(1.0);
        assertThat(ConfidencePolicy.normalize(-3)).isZero();
    }

    @Test
    void 中文数量解析() {
        assertQty("两个", "2", "个");
        assertQty("半盒", "0.5", "盒");
        assertQty("一个半", "1.5", "个");
        assertQty("十二个", "12", "个");
        assertQty("二十", "20", null);
        assertQty("x6", "6", null);
        assertQty("×3 根", "3", "根");
        assertQty("6个", "6", "个");
        assertQty("3.5斤", "3.5", "斤");
        assertQty("1个半", "1.5", "个");
        assertQty("适量", "1", null);
        assertQty(null, "1", null);
        assertQty("约 200 克", "200", "克");
    }

    private static void assertQty(String text, String amount, String unit) {
        ChineseQuantityParser.Quantity q = ChineseQuantityParser.parse(text);
        assertThat(q.amount()).as(text).isEqualByComparingTo(new BigDecimal(amount));
        assertThat(q.unit()).as(text + " unit").isEqualTo(unit);
    }

    @Test
    void 名称对齐_精确_别名_包含() {
        IngredientNameMatcher m = new IngredientNameMatcher(List.of(
                new IngredientNameMatcher.Entry(1L, "番茄", "tomato", 1L),
                new IngredientNameMatcher.Entry(2L, "鸡蛋", "egg", 4L),
                new IngredientNameMatcher.Entry(3L, "豆腐", "tofu", 5L),
                new IngredientNameMatcher.Entry(4L, "北豆腐", "tofu", 5L),
                new IngredientNameMatcher.Entry(5L, "小葱", "scallion", 1L)),
                Map.of("西红柿", "番茄", "香葱", "小葱"));
        assertThat(m.match("番茄").orElseThrow().how()).isEqualTo(IngredientNameMatcher.How.EXACT);
        IngredientNameMatcher.Match alias = m.match("西红柿").orElseThrow();
        assertThat(alias.entry().name()).isEqualTo("番茄");
        assertThat(alias.how()).isEqualTo(IngredientNameMatcher.How.ALIAS);
        assertThat(m.match(" 香葱 ").orElseThrow().entry().id()).isEqualTo(5L);
        // 包含：取最长的字典名
        assertThat(m.match("北豆腐块").orElseThrow().entry().name()).isEqualTo("北豆腐");
        assertThat(m.match("嫩豆腐").orElseThrow().entry().name()).isEqualTo("豆腐");
        assertThat(m.match("土鸡蛋").orElseThrow().entry().name()).isEqualTo("鸡蛋");
        assertThat(m.match("柠檬")).isEmpty();
        assertThat(m.match("")).isEmpty();
    }

    @Test
    void 识别任务生命周期() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 18, 0);
        assertThatThrownBy(() -> VisionJob.create(1L, VisionMode.FRIDGE, "https://evil.example/a.jpg", now))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> VisionMode.of("CAMERA")).isInstanceOf(DomainException.class);
        VisionJob job = VisionJob.create(1L, VisionMode.FRIDGE, "/gudu/uploads/original/20261016/1.jpg", now);
        assertThat(job.status()).isEqualTo(VisionJobStatus.PENDING);
        assertThat(job.imageExpiresAt()).isEqualTo(now.plusHours(24));
        assertThat(job.resultExpiresAt()).isEqualTo(now.plusDays(7));
        assertThatThrownBy(() -> job.markApplied(now)).hasMessageContaining("还没完成");
        job.complete(List.of(), null);
        assertThat(job.status()).isEqualTo(VisionJobStatus.DONE);
        assertThatThrownBy(() -> job.fail("x")).isInstanceOf(DomainException.class);
        job.markApplied(now);
        assertThatThrownBy(() -> job.markApplied(now)).hasMessageContaining("已经放进余量");
        job.clearImage();
        assertThat(job.imageUrl()).isNull();
        assertThat(job.ownedBy(1L)).isTrue();
        assertThat(job.ownedBy(2L)).isFalse();
    }

    @Test
    void 三种模式都有提示词与Schema() {
        for (VisionMode m : VisionMode.values()) {
            assertThat(VisionPrompts.promptOf(m)).contains("JSON");
            assertThat(VisionPrompts.schemaOf(m)).contains("\"type\":\"object\"");
        }
        assertThat(VisionPrompts.schemaOf(VisionMode.RECEIPT)).contains("price");
        assertThat(VisionPrompts.schemaOf(VisionMode.RECIPE)).contains("steps");
    }
}

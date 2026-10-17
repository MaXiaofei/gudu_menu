package com.gudu.xsd.modules.vision.infrastructure;

import com.gudu.xsd.modules.vision.domain.model.RawItem;
import com.gudu.xsd.modules.vision.domain.model.RawRecipe;
import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.port.VisionProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则示例识别（默认；模型网关未接入或不可用时兜底）。按模式返回确定结果，
 * 覆盖三档置信度（自动勾选 / 待确认 / 丢弃）与别名对齐（西红柿 → 番茄），便于演示与 E2E。
 */
@Component
public class MockVisionProvider implements VisionProvider {

    @Override
    public RecognitionResult recognize(VisionMode mode, String imageUrl) {
        return switch (mode) {
            case FRIDGE -> new RecognitionResult(List.of(
                    item("鸡蛋", 6, "个", 0.98),
                    item("牛奶", 1, "盒", 0.96),
                    item("西红柿", 3, "个", 0.94, "圣女果"),
                    item("北豆腐", 1, "块", 0.91),
                    item("西兰花", 1, "颗", 0.89),
                    item("胡萝卜", 2, "根", 0.87),
                    item("小葱", 1, "把", 0.83),
                    item("菠菜", 1, "把", 0.72, "油麦菜", "小白菜"),
                    item("香菇", 4, "朵", 0.64, "杏鲍菇", "口蘑"),
                    item("柠檬", 1, "个", 0.31)), null);
            case RECEIPT -> new RecognitionResult(List.of(
                    priced("鸡蛋", 10, "个", 0.97, 1290),
                    priced("猪里脊", 1, "块", 0.93, 2380),
                    priced("生抽", 1, "瓶", 0.90, 1150),
                    priced("小葱", 1, "把", 0.85, 200),
                    priced("娃娃菜", 1, "袋", 0.70, 590)), null);
            case RECIPE -> new RecognitionResult(List.of(
                    item("虾仁", 1, "盒", 0.92),
                    item("鸡蛋", 3, "个", 0.95),
                    item("小葱", 1, "根", 0.88)),
                    new RawRecipe("滑蛋虾仁", List.of(
                            new RawRecipe.Line("虾仁", "200", "克"),
                            new RawRecipe.Line("鸡蛋", "3", "个"),
                            new RawRecipe.Line("小葱", "1", "根"),
                            new RawRecipe.Line("盐", "适量", null)),
                            List.of("虾仁去虾线，加少许盐和淀粉抓匀腌 10 分钟。",
                                    "鸡蛋打散，加一小勺水淀粉搅匀。",
                                    "热锅少油，虾仁炒至变色盛出。",
                                    "倒入蛋液，半凝固时放回虾仁，轻推几下出锅，撒葱花。")));
        };
    }

    @Override
    public String name() {
        return "mock";
    }

    private static RawItem item(String name, int amount, String unit, double confidence, String... alternatives) {
        return new RawItem(name, BigDecimal.valueOf(amount), unit, confidence, List.of(alternatives), null);
    }

    private static RawItem priced(String name, int amount, String unit, double confidence, int priceFen) {
        return new RawItem(name, BigDecimal.valueOf(amount), unit, confidence, List.of(), priceFen);
    }
}

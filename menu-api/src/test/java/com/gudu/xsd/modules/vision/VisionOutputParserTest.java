package com.gudu.xsd.modules.vision;

import com.gudu.xsd.modules.vision.domain.model.RawItem;
import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.infrastructure.VisionOutputParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisionOutputParserTest {

    @Test
    void 容忍代码块围栏与前后废话() {
        String text = "好的，识别结果如下：\n```json\n{\"items\":[{\"name\":\"鸡蛋\",\"amount\":6,\"unit\":\"个\","
                + "\"confidence\":0.98}]}\n```\n以上。";
        RecognitionResult r = VisionOutputParser.parse(text, VisionMode.FRIDGE);
        assertThat(r.items()).hasSize(1);
        RawItem egg = r.items().get(0);
        assertThat(egg.name()).isEqualTo("鸡蛋");
        assertThat(egg.amount()).isEqualByComparingTo("6");
        assertThat(egg.unit()).isEqualTo("个");
        assertThat(egg.confidence()).isEqualTo(0.98);
    }

    @Test
    void 缺字段_非法置信度_中文数量() {
        String text = "{\"items\":["
                + "{\"name\":\"西红柿\",\"amount\":\"两个\",\"confidence\":\"92%\"},"
                + "{\"name\":\"菠菜\",\"quantity\":\"半把\",\"confidence\":\"中\",\"alternatives\":[\"油麦菜\",\"\",\"小白菜\"]},"
                + "{\"name\":\"牛奶\"},"
                + "{\"amount\":3},"
                + "{\"name\":\"  \"},"
                + "{\"name\":\"香菇\",\"amount\":-2,\"unit\":\"朵\",\"confidence\":75}"
                + "]}";
        RecognitionResult r = VisionOutputParser.parse(text, VisionMode.FRIDGE);
        assertThat(r.items()).extracting(RawItem::name).containsExactly("西红柿", "菠菜", "牛奶", "香菇");
        RawItem tomato = r.items().get(0);
        assertThat(tomato.amount()).isEqualByComparingTo("2");
        assertThat(tomato.unit()).isEqualTo("个");
        assertThat(tomato.confidence()).isEqualTo(0.92);
        RawItem spinach = r.items().get(1);
        assertThat(spinach.amount()).isEqualByComparingTo("0.5");
        assertThat(spinach.unit()).isEqualTo("把");
        assertThat(spinach.confidence()).isEqualTo(0.65);
        assertThat(spinach.alternatives()).containsExactly("油麦菜", "小白菜");
        RawItem milk = r.items().get(2);
        assertThat(milk.amount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(milk.confidence()).isEqualTo(0.6); // 缺 confidence 给中间值（进待确认）
        RawItem mushroom = r.items().get(3);
        assertThat(mushroom.amount()).isEqualByComparingTo(BigDecimal.ONE); // 负数回退 1
        assertThat(mushroom.confidence()).isEqualTo(0.75); // 百分制
    }

    @Test
    void 顶层直接是数组() {
        RecognitionResult r = VisionOutputParser.parse("[{\"name\":\"胡萝卜\",\"amount\":2,\"confidence\":0.9},\"小葱\"]",
                VisionMode.FRIDGE);
        assertThat(r.items()).extracting(RawItem::name).containsExactly("胡萝卜", "小葱");
    }

    @Test
    void 小票价格转分() {
        RecognitionResult r = VisionOutputParser.parse(
                "{\"items\":[{\"name\":\"生抽\",\"amount\":1,\"price\":11.5,\"confidence\":0.9},"
                        + "{\"name\":\"鸡蛋\",\"amount\":\"10个\",\"price\":\"￥12.90\",\"confidence\":0.95},"
                        + "{\"name\":\"小葱\",\"price\":\"免费\"}]}", VisionMode.RECEIPT);
        assertThat(r.items().get(0).priceFen()).isEqualTo(1150);
        assertThat(r.items().get(1).priceFen()).isEqualTo(1290);
        assertThat(r.items().get(1).amount()).isEqualByComparingTo("10");
        assertThat(r.items().get(2).priceFen()).isNull();
    }

    @Test
    void 菜谱截图_缺items时由用料补出_步骤去序号() {
        String text = "{\"title\":\"番茄炒蛋\",\"ingredients\":[{\"name\":\"番茄\",\"amount\":\"两个\"},"
                + "{\"name\":\"鸡蛋\",\"amount\":3,\"unit\":\"个\"},\"盐\"],"
                + "\"steps\":[\"1. 番茄切块\",\"2、鸡蛋打散\",{\"text\":\"3）下锅翻炒\"}]}";
        RecognitionResult r = VisionOutputParser.parse(text, VisionMode.RECIPE);
        assertThat(r.recipe().title()).isEqualTo("番茄炒蛋");
        assertThat(r.recipe().ingredients()).hasSize(3);
        assertThat(r.recipe().steps()).containsExactly("番茄切块", "鸡蛋打散", "下锅翻炒");
        assertThat(r.items()).extracting(RawItem::name).containsExactly("番茄", "鸡蛋", "盐");
        assertThat(r.items().get(0).amount()).isEqualByComparingTo("2");
    }

    @Test
    void 不是JSON时抛出() {
        assertThatThrownBy(() -> VisionOutputParser.parse("抱歉，我看不清这张图", VisionMode.FRIDGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisionOutputParser.parse("", VisionMode.FRIDGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisionOutputParser.parse("{\"items\":[", VisionMode.FRIDGE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.gudu.xsd.modules.agent.domain;

import com.gudu.xsd.modules.agent.domain.service.DishMentionGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 回复菜名一致性：卡片里没有的库内菜名、引号里像菜名的词都会被记下；卡片里有的、用户自己说的不算。 */
class DishMentionGuardTest {

    static final Set<String> KNOWN = Set.of("番茄炒蛋", "凉拌菠菜", "菠菜鸡蛋汤", "清蒸鲈鱼", "麻婆豆腐");

    @Test
    void 文本菜名超出卡片_被记下() {
        List<String> evidence = List.of("{type=recipes, items=[{name=番茄炒蛋}, {name=凉拌菠菜}, {name=菠菜鸡蛋汤}]}");
        String reply = "今晚推荐清蒸鲈鱼和「番茄烧豆腐」，再配个凉拌菠菜。";
        assertThat(DishMentionGuard.unsupported(reply, evidence, KNOWN)).containsExactly("清蒸鲈鱼", "番茄烧豆腐");
    }

    @Test
    void 只提卡片里的菜或用户原话里的菜_不记() {
        List<String> evidence = List.of("{name=凉拌菠菜}", "麻婆豆腐做完了，三个人吃的");
        assertThat(DishMentionGuard.unsupported("好的，凉拌菠菜很快；麻婆豆腐已帮你生成回写卡片。", evidence, KNOWN)).isEmpty();
        assertThat(DishMentionGuard.unsupported("", evidence, KNOWN)).isEmpty();
        assertThat(DishMentionGuard.unsupported("建议「少放盐」", evidence, KNOWN)).isEmpty();
    }
}

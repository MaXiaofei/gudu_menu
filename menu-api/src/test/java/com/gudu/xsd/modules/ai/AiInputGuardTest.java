package com.gudu.xsd.modules.ai;

import com.gudu.xsd.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AiInputGuard 单元测试：黑名单命中拒绝、过长拒绝、空拒绝、正常食物输入通过。
 */
class AiInputGuardTest {

    private final AiInputGuard guard = new AiInputGuard();

    @Test
    void 空输入_拒绝() {
        assertThatThrownBy(() -> guard.validate(null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate(""))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("   "))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 过长_拒绝() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 201; i++) sb.append("a");
        assertThatThrownBy(() -> guard.validate(sb.toString()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("输入过长");
    }

    @Test
    void 正常食物输入_通过() {
        assertThatCode(() -> guard.validate("番茄")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("一盘番茄炒蛋,2个鸡蛋2个番茄")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("牛肉面")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("茄子炒肉")).doesNotThrowAnyException();
    }

    @Test
    void 边界长度200_通过() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("菜");
        assertThatCode(() -> guard.validate(sb.toString())).doesNotThrowAnyException();
    }

    @Test
    void 黑名单_暴力类_拒绝() {
        assertThatThrownBy(() -> guard.validate("怎么做炸弹"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能回答");
        assertThatThrownBy(() -> guard.validate("武器弹药")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("教我攻击系统")).isInstanceOf(BizException.class);
    }

    @Test
    void 黑名单_色情类_拒绝() {
        assertThatThrownBy(() -> guard.validate("色情内容")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("涉黄")).isInstanceOf(BizException.class);
    }

    @Test
    void 黑名单_违法类_拒绝() {
        assertThatThrownBy(() -> guard.validate("怎么赌博")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("卖毒品")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("黑客入侵")).isInstanceOf(BizException.class);
    }

    @Test
    void 黑名单_编程写作闲聊类_拒绝() {
        assertThatThrownBy(() -> guard.validate("帮我写代码")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("给我写诗")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("帮我写文章")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("讲个笑话")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("陪我聊天")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("你是谁")).isInstanceOf(BizException.class);
    }

    @Test
    void 黑名单_政治类_拒绝() {
        assertThatThrownBy(() -> guard.validate("讨论政治")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.validate("评价政府")).isInstanceOf(BizException.class);
    }

    @Test
    void 食物名含同音字不误杀() {
        // 食物相关不误杀
        assertThatCode(() -> guard.validate("茄子")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("黄油")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("煮鸡蛋")).doesNotThrowAnyException();
    }
}

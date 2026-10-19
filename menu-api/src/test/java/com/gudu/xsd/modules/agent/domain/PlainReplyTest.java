package com.gudu.xsd.modules.agent.domain;

import com.gudu.xsd.modules.agent.domain.service.PlainReply;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 回复转纯文本：客户端不渲染 Markdown。 */
class PlainReplyTest {

    @Test
    void 粗体标题列表转纯文本() {
        String md = "## 今晚推荐\n\n**清蒸鲈鱼**：用掉明天到期的鲈鱼\n\n\n- 凉拌菠菜\n* 家常豆腐\n1. 西红柿鸡蛋汤\n";
        assertThat(PlainReply.of(md)).isEqualTo("今晚推荐\n\n清蒸鲈鱼：用掉明天到期的鲈鱼\n\n· 凉拌菠菜\n· 家常豆腐\n· 西红柿鸡蛋汤");
    }

    @Test
    void 纯文本不变_减号算式不误伤() {
        assertThat(PlainReply.of("北豆腐 -1 块，剩 0")).isEqualTo("北豆腐 -1 块，剩 0");
        assertThat(PlainReply.of("")).isEmpty();
        assertThat(PlainReply.of(null)).isNull();
    }
}

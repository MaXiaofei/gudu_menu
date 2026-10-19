package com.gudu.xsd.modules.community.application.ai;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.application.ModelUnavailableException;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.community.domain.model.ModerationVerdict;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;
import com.gudu.xsd.modules.community.domain.service.PostTextWriter.Material;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 厨房圈接入模型网关：AI 帮写文案、AI 预审（规则先行、只升不降、超时与失败退回规则）。 */
class CommunityLlmTest {

    private final ModelGateway gateway = mock(ModelGateway.class);
    private final Material material = new Material("滑蛋虾仁", 10, "今天 18:40", "简单");

    private static ChatResult json(String s) {
        return new ChatResult(ModelReply.text(s), "deepseek", "deepseek-flash", false, 10, BigDecimal.ZERO, List.of());
    }

    @Test
    void 文案_模型输出清洗_超长截断() {
        when(gateway.chatText(eq("community_text"), anyString(), contains("滑蛋虾仁"))).thenReturn("“虾仁滑嫩，**十分钟**上桌。”");
        assertThat(new LlmPostTextWriter(gateway).write(material, "short")).isEqualTo("虾仁滑嫩，十分钟上桌。");

        String longText = "好吃。".repeat(120);
        assertThat(LlmPostTextWriter.clean(longText).length()).isLessThanOrEqualTo(LlmPostTextWriter.MAX_LEN);
        assertThat(LlmPostTextWriter.userPrompt(material, "casual")).contains("口语").contains("10 分钟");
    }

    @Test
    void 文案_模型不可用或空输出_退回模板() {
        when(gateway.chatText(anyString(), anyString(), anyString())).thenThrow(new ModelUnavailableException("x"));
        assertThat(new LlmPostTextWriter(gateway).write(material, "short")).contains("滑蛋虾仁");
        ModelGateway empty = mock(ModelGateway.class);
        when(empty.chatText(anyString(), anyString(), anyString())).thenReturn("  ");
        assertThat(new LlmPostTextWriter(empty).write(material, "default")).contains("滑蛋虾仁");
    }

    @Test
    void 预审_规则判高风险时不调模型() {
        LlmPostModerator m = new LlmPostModerator(gateway, Runnable::run);
        ModerationVerdict v = m.review("好物", "加微信 abc12345 领优惠券 返现", List.of());
        assertThat(v.risk()).isEqualTo(RiskLevel.HIGH);
        verify(gateway, never()).chatJson(anyString(), anyString(), anyString());
    }

    @Test
    void 预审_模型判高风险时升级_原因带AI前缀_内容包在post标签里() {
        when(gateway.chatJson(eq("community_moderation"), anyString(), contains("<post>")))
                .thenReturn(json("{\"risk\":\"HIGH\",\"reasons\":[\"疑似引流到外部店铺\"],\"scores\":[{\"label\":\"引流广告\",\"score\":0.9}]}"));
        ModerationVerdict v = new LlmPostModerator(gateway, Runnable::run).review("今天的晚饭", "店里同款，私我拿", List.of());
        assertThat(v.risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(v.reasons()).contains("AI：疑似引流到外部店铺");
        assertThat(v.scores()).anyMatch(s -> s.label().equals("引流广告") && s.score() == 0.9);
    }

    @Test
    void 预审_模型判低不降级规则的中风险() {
        when(gateway.chatJson(anyString(), anyString(), anyString())).thenReturn(json("{\"risk\":\"LOW\",\"reasons\":[]}"));
        ModerationVerdict v = new LlmPostModerator(gateway, Runnable::run).review("晒菜", "附上优惠券信息", List.of());
        assertThat(v.risk()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void 预审_模型失败_不可解析_超时_都退回规则结果() {
        when(gateway.chatJson(anyString(), anyString(), anyString())).thenThrow(new ModelUnavailableException("x"));
        assertThat(new LlmPostModerator(gateway, Runnable::run).review("晒菜", "清蒸鲈鱼", List.of()).risk())
                .isEqualTo(RiskLevel.LOW);

        ModelGateway prose = mock(ModelGateway.class);
        when(prose.chatJson(anyString(), anyString(), anyString())).thenReturn(json("看起来没问题"));
        assertThat(new LlmPostModerator(prose, Runnable::run).review("晒菜", "清蒸鲈鱼", List.of()).reasons()).isEmpty();

        ModelGateway slow = mock(ModelGateway.class);
        when(slow.chatJson(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(LlmPostModerator.TIMEOUT_MS + 1000);
            return json("{\"risk\":\"HIGH\"}");
        });
        long t0 = System.currentTimeMillis();
        ModerationVerdict v = new LlmPostModerator(slow).review("晒菜", "清蒸鲈鱼", List.of());
        assertThat(v.risk()).isEqualTo(RiskLevel.LOW);
        assertThat(System.currentTimeMillis() - t0).isLessThan(LlmPostModerator.TIMEOUT_MS + 900);
    }

    @Test
    void 预审_禁发词仍由规则同步拒绝() {
        assertThatThrownBy(() -> new LlmPostModerator(gateway, Runnable::run).assertPublishable("赌博", ""))
                .isInstanceOf(DomainException.class);
    }
}

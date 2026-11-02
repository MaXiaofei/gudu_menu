package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 咕嘟助手 / 问咕嘟紧急关停的判定（按端；出现内容风险时一键关闭，默认开）。
 *
 * <p>只拦「发新消息 / 生成」；查看、删除会话与历史、确认 / 取消 / 撤销、反馈、建议均不受影响。
 * 咕嘟助手（该端）关停 → 拒绝所有发消息（不看客户端自报的 page，返回 5032）；
 * 问咕嘟关停 → 只拒绝 page == 'cook_mode'（5033）。即跟做模式里提问需要两个开关都开，
 * 避免客户端改 page 值绕过其中一个开关。
 */
@Component
public class AgentSwitchPolicy {

    public static final int CODE_AGENT_SUSPENDED = 5032;
    public static final String MSG_AGENT_SUSPENDED = "咕嘟助手暂停服务";
    public static final int CODE_COOK_ASK_SUSPENDED = 5033;
    public static final String MSG_COOK_ASK_SUSPENDED = "问咕嘟暂停服务";

    private static final String COOK_MODE_PAGE = "cook_mode";

    /** 被拦截的结果：业务码 + 提示语。 */
    public record Blocked(int code, String message) {
    }

    private final FeatureSwitchAppService switches;

    public AgentSwitchPolicy(FeatureSwitchAppService switches) {
        this.switches = switches;
    }

    /** 发消息前检查：开关关闭返回 Blocked，开放返回空。先看咕嘟助手（所有对话入口），再看跟做模式的问咕嘟。 */
    public Optional<Blocked> checkSend(Map<String, Object> context, Platform platform) {
        if (!switches.isEnabled(FeatureKey.AGENT_CHAT, platform)) {
            return Optional.of(new Blocked(CODE_AGENT_SUSPENDED, MSG_AGENT_SUSPENDED));
        }
        if (isCookMode(context) && !switches.isEnabled(FeatureKey.AGENT_COOK_ASK, platform)) {
            return Optional.of(new Blocked(CODE_COOK_ASK_SUSPENDED, MSG_COOK_ASK_SUSPENDED));
        }
        return Optional.empty();
    }

    private static boolean isCookMode(Map<String, Object> context) {
        Object page = context == null ? null : context.get("page");
        return COOK_MODE_PAGE.equals(page);
    }
}

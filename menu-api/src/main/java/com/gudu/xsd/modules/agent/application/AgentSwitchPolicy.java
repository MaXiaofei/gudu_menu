package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 咕嘟助手 / 问咕嘟紧急关停的判定（出现内容风险时一键关闭，默认开）。
 *
 * <p>只拦「发新消息 / 生成」；查看、删除会话与历史、确认 / 取消 / 撤销、反馈、建议均不受影响。
 * context.page == 'cook_mode' 时看「问咕嘟」开关，其余看「咕嘟助手」开关。
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

    /** 该请求上下文对应哪个开关。 */
    public static FeatureKey keyOf(Map<String, Object> context) {
        Object page = context == null ? null : context.get("page");
        return COOK_MODE_PAGE.equals(page) ? FeatureKey.AGENT_COOK_ASK : FeatureKey.AGENT_CHAT;
    }

    /** 发消息前检查：开关关闭返回 Blocked，开放返回空。 */
    public Optional<Blocked> checkSend(Map<String, Object> context) {
        FeatureKey key = keyOf(context);
        if (switches.isEnabled(key)) return Optional.empty();
        return Optional.of(key == FeatureKey.AGENT_COOK_ASK
                ? new Blocked(CODE_COOK_ASK_SUSPENDED, MSG_COOK_ASK_SUSPENDED)
                : new Blocked(CODE_AGENT_SUSPENDED, MSG_AGENT_SUSPENDED));
    }
}

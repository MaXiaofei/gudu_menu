package com.gudu.xsd.modules.contentsecurity.domain.model;

/**
 * 文本检测结论。
 *
 * @param suggest 建议
 * @param label   微信命中标签（100 正常 / 10001 广告 / 20001 时政 / 20002 色情 / 20003 辱骂 / 20006 违法犯罪 …；规则命中为 0）
 * @param reason  命中原因（规则命中词或微信标签，仅进日志，不回给用户）
 * @param source  结论来源
 * @param traceId 微信请求 trace_id（规则为 null）
 */
public record TextVerdict(Suggest suggest, int label, String reason, Source source, String traceId) {

    /** 结论来源。 */
    public enum Source {
        /** 微信 msgSecCheck。 */
        WX,
        /** 规则（未走微信：开关关闭 / 无 openid，或规则先行命中）。 */
        RULE,
        /** 微信不可用，降级到规则。 */
        RULE_FALLBACK,
        /** 微信不可用且该用途失败即拒绝。 */
        UNAVAILABLE
    }

    public TextVerdict {
        suggest = suggest == null ? Suggest.PASS : suggest;
        source = source == null ? Source.RULE : source;
    }

    public static TextVerdict pass(Source source) {
        return new TextVerdict(Suggest.PASS, 0, null, source, null);
    }

    public static TextVerdict ruleHit(String word) {
        return new TextVerdict(Suggest.RISKY, 0, "规则命中：" + word, Source.RULE, null);
    }

    public static TextVerdict unavailable(String reason) {
        return new TextVerdict(Suggest.PASS, 0, reason, Source.UNAVAILABLE, null);
    }

    public boolean isUnavailable() {
        return source == Source.UNAVAILABLE;
    }

    public boolean blocks(ContentPurpose purpose) {
        return !isUnavailable() && suggest.blocks(purpose);
    }

    /** 换来源（规则结论被用作降级结果时标记 RULE_FALLBACK）。 */
    public TextVerdict withSource(Source s) {
        return new TextVerdict(suggest, label, reason, s, traceId);
    }
}

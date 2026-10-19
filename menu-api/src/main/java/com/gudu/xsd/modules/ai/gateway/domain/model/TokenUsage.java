package com.gudu.xsd.modules.ai.gateway.domain.model;

/**
 * token 用量。
 *
 * @param promptTokens     输入总量（含缓存命中）
 * @param cacheHitTokens   输入中命中缓存的部分（DeepSeek prompt_cache_hit_tokens）
 * @param completionTokens 输出（含思考）
 */
public record TokenUsage(int promptTokens, int cacheHitTokens, int completionTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public TokenUsage {
        promptTokens = Math.max(0, promptTokens);
        cacheHitTokens = Math.max(0, Math.min(cacheHitTokens, promptTokens));
        completionTokens = Math.max(0, completionTokens);
    }

    public int cacheMissTokens() {
        return promptTokens - cacheHitTokens;
    }

    public TokenUsage plus(TokenUsage o) {
        if (o == null) return this;
        return new TokenUsage(promptTokens + o.promptTokens, cacheHitTokens + o.cacheHitTokens,
                completionTokens + o.completionTokens);
    }
}

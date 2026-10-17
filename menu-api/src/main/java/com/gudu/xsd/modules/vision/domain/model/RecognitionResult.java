package com.gudu.xsd.modules.vision.domain.model;

import java.util.List;

/**
 * 一次识别的原始结果（items 必有；recipe 仅 RECIPE 模式）。
 *
 * @param degraded 未用到真实视觉模型的主目标（降级到后备模型或 Mock 示例）
 */
public record RecognitionResult(List<RawItem> items, RawRecipe recipe, boolean degraded) {

    public RecognitionResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public RecognitionResult(List<RawItem> items, RawRecipe recipe) {
        this(items, recipe, false);
    }

    public RecognitionResult asDegraded() {
        return degraded ? this : new RecognitionResult(items, recipe, true);
    }
}

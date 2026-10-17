package com.gudu.xsd.modules.vision.domain.model;

import java.util.List;

/** 一次识别的原始结果（items 必有；recipe 仅 RECIPE 模式）。 */
public record RecognitionResult(List<RawItem> items, RawRecipe recipe) {

    public RecognitionResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

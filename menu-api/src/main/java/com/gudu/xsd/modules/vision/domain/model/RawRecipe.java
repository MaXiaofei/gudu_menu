package com.gudu.xsd.modules.vision.domain.model;

import java.util.List;

/** 菜谱截图识别结果：标题 + 用料行 + 步骤。 */
public record RawRecipe(String title, List<Line> ingredients, List<String> steps) {

    public RawRecipe {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 用料行：名称 + 用量文本（如「2 个」「适量」）+ 单位（可空）。 */
    public record Line(String name, String amount, String unit) {
    }
}

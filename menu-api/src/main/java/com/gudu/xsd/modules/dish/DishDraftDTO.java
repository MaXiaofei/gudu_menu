package com.gudu.xsd.modules.dish;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 写菜谱草稿（DESIGN.md §16.4）。
 * 存草稿不校验必填（name 可空 = 未命名草稿）；用量自由文本原样存，发布时再解析。
 */
public class DishDraftDTO {

    /** 保存入参：id 为空 = 新建草稿，非空 = 更新。 */
    @Data
    public static class Save {
        private Long id;
        private String name;
        private String coverUrl;
        private Integer prepTime;
        private Integer cookTime;
        private Integer difficulty;
        private String note;
        private List<Long> tagIds;
        private List<Long> cuisineIds;
        private List<DraftIngredient> ingredients;
        private List<DishStep> steps;
        private String sourceUrl;
    }

    /** 用料行（数字 + 单位原文分存，回填时还原两个输入框）。 */
    @Data
    public static class DraftIngredient {
        private Long ingredientId;
        private String ingredientName;
        /** 用量数字原文（如 "2"），可空。 */
        private String amount;
        /** 单位原文（如 "个"/"适量"/"斤"），可空；提交时匹配字典，匹配不到自动补字典。 */
        private String unitText;
        /** 旧草稿兼容：自由文本（已拆分为 amount + unitText，保留字段不删）。 */
        private String amountText;
    }

    /** 草稿箱列表项（轻量，不拉 JSON 明细）。 */
    @Data
    public static class ListItem {
        private Long id;
        private String name;
        private String coverUrl;
        private int ingredientCount;
        private int stepCount;
        private LocalDateTime updateTime;
    }

    /** 草稿详情（恢复编辑回填用，含 JSON 明细）。 */
    @Data
    public static class Detail {
        private Long id;
        private String name;
        private String coverUrl;
        private Integer prepTime;
        private Integer cookTime;
        private Integer difficulty;
        private String note;
        private List<Long> tagIds;
        private List<Long> cuisineIds;
        private List<DraftIngredient> ingredients;
        private List<DishStep> steps;
        private String sourceUrl;
        private LocalDateTime updateTime;
    }
}

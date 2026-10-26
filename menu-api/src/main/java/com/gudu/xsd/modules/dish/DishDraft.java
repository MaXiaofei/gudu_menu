package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 写菜谱草稿（DESIGN.md §16.4）：独立表不污染 dish，发布后删除。
 * 用量自由文本原样存 JSON（ingredients），恢复时回填输入框，发布时再解析 amount/unitId。
 */
@Data
@TableName("dish_draft")
public class DishDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long userId;

    private String name;

    private String coverUrl;

    private Integer prepTime;

    private Integer cookTime;

    private Integer difficulty;

    /** 菜谱介绍。 */
    private String note;

    /** 标签 dict id 列表（JSON 原文）。 */
    private String tags;

    /** 菜系 dict id（单选，JSON 原文；可空，§16.2）。 */
    private String cuisineIds;

    /** 用料 [{ingredientId, ingredientName, amountText}]（JSON 原文）。 */
    private String ingredients;

    /** 步骤 [{seq, text, images}]（JSON 原文）。 */
    private String steps;

    /** manual / url。 */
    private String sourceType;

    /** 导入链接原文。 */
    private String sourceUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

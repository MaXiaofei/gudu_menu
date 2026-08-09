package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食材库存档位（V42）：每食材一行，模糊 3 档，不做克数。
 *
 * <p>替换 pantry 批次表的 APP 语义：用户手动维护（做菜确认用完/采购入库/手动修正），
 * 系统不再自动扣减。pantry 表保留不再更新（admin 过渡读，P5 迁移后废弃）。
 */
@Data
@TableName("ingredient_stock")
public class IngredientStock {

    public static final String LEVEL_ENOUGH = "ENOUGH";
    public static final String LEVEL_LOW = "LOW";
    public static final String LEVEL_NONE = "NONE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    /** ENOUGH 充足 / LOW 快用完 / NONE 没有。 */
    private String level;

    private LocalDateTime updateTime;
}

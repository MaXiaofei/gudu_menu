package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
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

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long ingredientId;

    /** ENOUGH 充足 / LOW 快用完 / NONE 没有。 */
    private String level;

    /** 保质期（V64，可空）。今天到期 daysLeft=0。 */
    private LocalDate bestBefore;

    /** 开封/首次用了一些的时间（V64，可空）。 */
    private LocalDateTime openedAt;

    private LocalDateTime updateTime;
}

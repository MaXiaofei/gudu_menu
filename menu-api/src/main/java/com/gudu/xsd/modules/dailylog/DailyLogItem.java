package com.gudu.xsd.modules.dailylog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 每日饮食记录的明细：一条摄入项(菜品或食材)。
 * dish_id 与 ingredient_id 二选一：dish 项 amount 为份数、ingredient 项 amount 为克。
 */
@Data
@TableName("daily_log_item")
public class DailyLogItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long logId;

    private Long dishId;

    private Long ingredientId;

    private BigDecimal amount;

    private BigDecimal servingFactor;
}

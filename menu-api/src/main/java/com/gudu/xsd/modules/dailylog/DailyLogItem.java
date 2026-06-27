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

    private Long logId;

    private Long dishId;

    private Long ingredientId;

    private BigDecimal amount;

    private BigDecimal servingFactor;
}

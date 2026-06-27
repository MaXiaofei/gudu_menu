package com.gudu.xsd.modules.shopping;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购清单：一次「按周计划生成」对应一条。
 * source_plan_id 关联 meal_plan；time_range/start_date/end_date 标识本次覆盖的时间范围。
 */
@Data
@TableName("shopping_list")
public class ShoppingList {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源周计划 meal_plan.id（可空，允许手工生成）。 */
    private Long sourcePlanId;

    /** 时间范围标识（如 week / day）。 */
    private String timeRange;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}

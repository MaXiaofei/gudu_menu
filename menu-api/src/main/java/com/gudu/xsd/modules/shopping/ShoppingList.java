package com.gudu.xsd.modules.shopping;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购清单：一次「生成」对应一条。
 * time_range/start_date/end_date 标识本次覆盖的时间范围。
 */
@Data
@TableName("shopping_list")
public class ShoppingList {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    /** 来源周计划 meal_plan.id（历史遗留字段，周计划已下线，不再写入）。 */
    private Long sourcePlanId;

    /** 来源食集 menu.id（可空，dish/custom 来源留空）。Plan E 加。 */
    private Long sourceMenuId;

    /** 清单名（自定义采购，可空）。V44。 */
    private String name;

    /** 时间范围标识（如 week / day）。 */
    private String timeRange;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}

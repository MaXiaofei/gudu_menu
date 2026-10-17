package com.gudu.xsd.modules.mealplan.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** meal_plan_slot 持久化对象（V72）。badges 逗号分隔。 */
@Data
@TableName("meal_plan_slot")
public class MealPlanSlotPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private LocalDate planDate;
    private String meal;
    private Long dishId;
    private String badges;
}

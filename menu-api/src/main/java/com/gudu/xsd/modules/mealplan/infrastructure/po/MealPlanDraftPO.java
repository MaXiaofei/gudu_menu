package com.gudu.xsd.modules.mealplan.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** meal_plan_draft 持久化对象（V72）。约束与校验结论存 JSON。 */
@Data
@TableName("meal_plan_draft")
public class MealPlanDraftPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String requestJson;
    private String constraintsJson;
    private String checkJson;
    private String status;
    private String appliedMenuIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

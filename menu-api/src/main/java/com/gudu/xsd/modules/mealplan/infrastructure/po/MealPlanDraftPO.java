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

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;
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

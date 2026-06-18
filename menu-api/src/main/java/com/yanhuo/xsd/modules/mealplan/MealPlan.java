package com.yanhuo.xsd.modules.mealplan;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("meal_plan")
public class MealPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 周起始（周一）。 */
    private LocalDate weekStart;

    private String name;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}

package com.gudu.xsd.modules.mealplan;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "menu_template", autoResultMap = true)
public class MenuTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 一周菜排列快照：List<MealPlanItem> 视图（JSON 列）。 */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<MealPlanItem> snapshot;

    private LocalDateTime createTime;
}

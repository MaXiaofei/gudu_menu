package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 营养指标维度。unit: g/mg/kcal/index；metric_group: macro(宏量)/micro(微量)/gi。
 */
@Data
@TableName("nutrition_metric")
public class NutritionMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String unit;

    private String metricGroup;

    private Integer sort;
}

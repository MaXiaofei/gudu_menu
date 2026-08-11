package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 食材单位换算：1 个某单位 = 多少克。一个食材可有多条（鸡蛋：个=50g 默认、盒=300g）。
 * is_default=1 的行决定该食材录入默认单位 + 价格计价单位（应用层约束每食材至多一条）。
 */
@Data
@TableName("ingredient_unit_gram")
public class IngredientUnitGram {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    /** → sys_dict(group=unit)。 */
    private Long unitId;

    /** 1 个该单位 = 多少克。 */
    private BigDecimal gramsPerUnit;

    /** 是否该食材的默认单位（录入/计价用）。 */
    private Integer isDefault;

    /** 聚合计数（非表字段：列表换算条数统计用）。 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer cnt;
}

package com.yanhuo.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("ingredient")
public class Ingredient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 关联 sys_dict(unit)。 */
    private Long unitId;

    private BigDecimal price;

    /** 关联 sys_dict(purchase_category)。 */
    private Long purchaseCategoryId;

    private Integer purchaseCount;

    private Integer usageCount;

    @TableLogic
    private Integer deleted;
}

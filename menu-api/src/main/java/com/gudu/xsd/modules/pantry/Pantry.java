package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 食材库存：记录家中现有食材的余量/单位/过期日。
 * ingredient_id 关联 ingredient；unit_id 关联 sys_dict(group=unit)。
 * 低库存阈值已挪到 ingredient.low_threshold（V39）。
 */
@Data
@TableName("pantry")
public class Pantry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    private BigDecimal amount;

    private Long unitId;

    /** 内部记账基准克数（盘点用 amount+unitId，扣减/余色用 grams）。 */
    private BigDecimal grams;

    private LocalDate expireDate;

    /** 存放方式：常温/冷藏/冷冻（手动添加批次属性，可空）。V41。 */
    private String storage;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}

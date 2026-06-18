package com.yanhuo.xsd.modules.pantry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 食材库存：记录家中现有食材的余量/单位/过期日/低库存阈值。
 * ingredient_id 关联 ingredient；unit_id 关联 sys_dict(group=unit)。
 */
@Data
@TableName("pantry")
public class Pantry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    private BigDecimal amount;

    private Long unitId;

    private LocalDate expireDate;

    private BigDecimal lowThreshold;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}

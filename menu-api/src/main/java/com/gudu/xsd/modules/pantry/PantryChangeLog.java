package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变动流水：记录每一笔库存变动的来源/变动量/变动后合计/备注。
 * 支撑库存页「每行来源标签」+ 食材详情页「最近 N 条操作明细」。
 */
@Data
@TableName("pantry_change_log")
public class PantryChangeLog {

    public static final String SOURCE_COOK = "cook";
    public static final String SOURCE_PURCHASE = "purchase";
    public static final String SOURCE_INVENTORY = "inventory";
    public static final String SOURCE_MANUAL = "manual";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    /** 来源：cook 做菜 / purchase 采购 / inventory 盘点 / manual 手动。 */
    private String source;

    /** 变动量（正入负出，克）。 */
    private BigDecimal delta;

    /** 变动后该食材合计（克）。 */
    private BigDecimal amountAfter;

    /** 来源备注（手动：朋友送/赠品/旧库存补登）。 */
    private String sourceNote;

    private LocalDateTime createTime;
}

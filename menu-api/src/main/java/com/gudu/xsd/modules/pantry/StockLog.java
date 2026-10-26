package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存档位变动流水（V42 简版）：记录 用完了/用了一些/采购入库/手动修正。
 * 支撑库存页「上次变动」标签与详情页流水。
 */
@Data
@TableName("stock_log")
public class StockLog {

    public static final String ACTION_COOK = "cook"; // 做菜确认·用完了
    public static final String ACTION_COOK_PARTIAL = "cook_partial"; // 做菜确认·用了一些
    public static final String ACTION_PURCHASE = "purchase"; // 采购勾选已买入库
    public static final String ACTION_MANUAL = "manual"; // 手动修正/入库
    public static final String ACTION_UNDO = "undo"; // 撤回入库（恢复入库前档位）

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long ingredientId;

    private String action;

    /** 变动前档位（新建档为 null）。V43。撤回入库时恢复用。 */
    private String beforeLevel;

    /** 变动后档位。V43。 */
    private String afterLevel;

    /** 变动前保质期快照（V69，做菜用掉时记录）。 */
    private java.time.LocalDate bestBefore;

    /** 备注（手动：朋友送/赠品/旧库存补登）。 */
    private String note;

    /** 溯源（采购入库/撤回 = shopping_item.id）。V43。撤回按它查流水。 */
    private Long refId;

    private LocalDateTime createTime;
}

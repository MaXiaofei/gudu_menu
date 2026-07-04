package com.gudu.xsd.modules.menu.prep;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食集备料状态（menu_prep_status 表）。
 *
 * <p>一个 (menuId, ingredientId) 对应一条记录；无记录即视为 {@link PrepStatus#PENDING}。
 * 参照 {@link com.gudu.xsd.modules.shopping.ShoppingItem} 实体范式。
 */
@Data
@TableName("menu_prep_status")
public class MenuPrepStatus {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long menuId;

    private Long ingredientId;

    /** {@link PrepStatus} 名（大写）。 */
    private String status;

    private LocalDateTime updateTime;
}

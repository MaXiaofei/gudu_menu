package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("menu_dish")
public class MenuDish {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long menuId;

    private Long dishId;

    /** 该菜在该菜单的份数。 */
    private BigDecimal servingFactor;

    /** 该菜在食集中的备注（如「宝宝那份少盐」；空=无备注）。V40 加。 */
    private String note;
}

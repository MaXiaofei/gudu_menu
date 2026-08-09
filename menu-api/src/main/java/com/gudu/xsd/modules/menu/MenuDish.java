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

    /** 菜品 id（自定义菜名时为空，V45）。 */
    private Long dishId;

    /** 自定义菜名（dishId 为空时用，V45 聚餐朋友自由输入）。 */
    private String customName;

    /** 谁加的：null=房主；朋友=其 member_id。V45。 */
    private Long addedByMemberId;

    /** 冗余昵称（房主加的不填，列表展示「XX 点的」）。V45。 */
    private String addedByNickname;

    /** 该菜在该食集的份数。 */
    private BigDecimal servingFactor;

    /** 该菜在食集中的备注（如「宝宝那份少盐」；空=无备注）。V40 加。 */
    private String note;
}

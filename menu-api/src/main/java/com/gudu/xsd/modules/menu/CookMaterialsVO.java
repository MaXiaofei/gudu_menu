package com.gudu.xsd.modules.menu;

import java.math.BigDecimal;
import java.util.List;

/**
 * 做菜确认弹窗数据（GET /menu/{id}/cook-materials，V42）。
 * 本次用到的食材 + 当前档位 + 是否调料（弹窗默认值：食材=用完了，调料=用了一些）。
 */
public record CookMaterialsVO(Long menuId, List<Item> items) {

    /**
     * @param needGrams   聚合用量（显示用，不参与任何库存判断）
     * @param level       ENOUGH 充足 / LOW 快用完 / NONE 没有（当前档位）
     * @param isCondiment 是否调料（采购品类=调味料）
     */
    public record Item(Long ingredientId, String ingredientName,
                       BigDecimal needGrams, String level, boolean isCondiment) {
    }
}

package com.gudu.xsd.modules.menu;

import java.util.List;

/**
 * 做菜确认弹窗数据（GET /menu/{id}/cook-materials，V42）。
 * 本次用到的食材 + 用量原文 + 当前档位 + 是否调料（弹窗默认值：食材=用完了，调料=用了一些）。
 */
public record CookMaterialsVO(Long menuId, List<Item> items) {

    /**
     * @param usageTexts  用量原文（已拼菜名，如「番茄炒蛋 2个」；显示用，不参与任何库存判断）
     * @param level       ENOUGH 充足 / LOW 快用完 / NONE 没有（当前档位）
     * @param isCondiment 是否调料（采购品类=调味料）
     */
    public record Item(Long ingredientId, String ingredientName,
                       List<String> usageTexts, String level, boolean isCondiment) {
    }
}

package com.gudu.xsd.modules.menu.prep;

import java.util.List;

/**
 * 备菜聚合返回（GET /menu/{id}/prep）。
 *
 * <p>主料进 items；调料（purchaseCategoryId=调味料品类）折叠到 condiments。两者都计入
 * readyCount/totalCount（调料也是备料动作）。
 */
public record MenuPrepVO(
        /** 主料列表（purchaseCategoryId != 调味料品类）。 */
        List<PrepItemVO> items,
        /** 调料折叠组（purchaseCategoryId = 调味料品类，如盐/油/豉油）。 */
        List<PrepItemVO> condiments,
        /** 已备数（items + condiments 中 status=READY 的数量）。 */
        int readyCount,
        /** 共需备料数（= items.size() + condiments.size()，含调料）。 */
        int totalCount
) {}

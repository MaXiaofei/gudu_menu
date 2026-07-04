package com.gudu.xsd.modules.menu.prep;

import java.util.List;

/**
 * 备菜聚合返回（GET /menu/{id}/prep）。
 *
 * <p>主料 items 进主列表（需备料、计入进度）；调料 condiments 折叠（无需备料、不计进度）。
 */
public record MenuPrepVO(
        /** 主料列表（purchaseCategoryId != 调味料品类）。 */
        List<PrepItemVO> items,
        /** 调料折叠组（purchaseCategoryId = 调味料品类，如盐/油/豉油）。 */
        List<PrepItemVO> condiments,
        /** 已备数（items 中 status=READY 的数量）。 */
        int readyCount,
        /** 共需备料数（= items.size()，不含调料）。 */
        int totalCount
) {}

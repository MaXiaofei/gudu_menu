package com.gudu.xsd.modules.menu.prep;

import java.util.List;

/**
 * 备菜聚合返回（GET /menu/{id}/prep）。
 *
 * <p>主料与调料统一进 items（需备料、全部计入进度）；condiments 保留字段、恒为空。
 */
public record MenuPrepVO(
        /** 全部用料（purchaseCategoryId 不限，含调味料）。 */
        List<PrepItemVO> items,
        /** 调料折叠组（已废弃：不再折叠，恒为空，兼容旧客户端）。 */
        List<PrepItemVO> condiments,
        /** 已备数（items 中 status=READY 的数量）。 */
        int readyCount,
        /** 共需备料数（= items.size()，含调料）。 */
        int totalCount
) {}

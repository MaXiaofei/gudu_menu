package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;

import java.util.Map;

/** contracts.md DishCard（对外，首屏建议等复用）。 */
public final class DishCards {

    private DishCards() {
    }

    public static Map<String, Object> of(DishInfo d) {
        return ToolSupport.dishCard(d);
    }
}

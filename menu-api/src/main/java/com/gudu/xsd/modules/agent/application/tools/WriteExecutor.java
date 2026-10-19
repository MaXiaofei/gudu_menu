package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.ItemLine;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.PantryLine;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.WriteResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/** 确认后执行写操作：按规范化参数（已解析的 id）调用 {@link Kitchen} 的写方法。 */
public final class WriteExecutor {

    private WriteExecutor() {
    }

    public static WriteResult execute(Kitchen k, PendingAction a) {
        Map<String, Object> args = a.args();
        return switch (a.tool()) {
            case AddToMenuTool.NAME -> {
                List<Long> dishIds = ids(args.get("dishIds"));
                Map<String, Object> nm = map(args, "newMenu");
                if (args.get("menuId") != null) {
                    yield k.addDishesToMenu(((Number) args.get("menuId")).longValue(), dishIds);
                }
                if (nm.isEmpty()) throw new DomainException("缺少食集");
                Integer servings = integer(nm, "servings");
                yield k.createMenu(str(nm, "name"), LocalDate.parse(str(nm, "date")),
                        servings == null ? 1 : servings, dishIds);
            }
            case RemoveFromMenuTool.NAME ->
                    k.removeDishesFromMenu(((Number) args.get("menuId")).longValue(), ids(args.get("dishIds")));
            case AddToShoppingTool.NAME -> k.addShopping(objects(args, "items").stream()
                    .map(m -> new ItemLine(str(m, "name"), str(m, "amount"), str(m, "unit"))).toList());
            case MarkCookedTool.NAME -> k.markCooked(((Number) args.get("dishId")).longValue(),
                    integer(args, "servings"), ids(args.get("eaterIds")),
                    str(args, "date") == null ? null : LocalDate.parse(str(args, "date")),
                    objects(args, "overrides").stream()
                            .map(m -> new ItemLine(str(m, "name"), str(m, "amount"), str(m, "unit"))).toList());
            case AddPantryTool.NAME -> k.addPantry(objects(args, "items").stream()
                    .map(m -> new PantryLine(str(m, "name"), str(m, "amount"), str(m, "unit"),
                            str(m, "bestBefore") == null ? null : LocalDate.parse(str(m, "bestBefore")))).toList());
            default -> throw new DomainException("未知的写操作：" + a.tool());
        };
    }

    static List<Long> ids(Object raw) {
        List<Long> out = new ArrayList<>();
        if (raw instanceof Collection<?> c) {
            for (Object o : c) {
                if (o instanceof Number n) out.add(n.longValue());
                else if (o != null) out.add(Long.parseLong(String.valueOf(o)));
            }
        }
        return out;
    }
}

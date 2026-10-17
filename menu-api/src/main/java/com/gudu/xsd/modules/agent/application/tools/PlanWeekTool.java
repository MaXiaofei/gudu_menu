package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishQuery;
import com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gudu.xsd.modules.agent.application.tools.ToolSupport.*;

/**
 * plan_week（读，产出草稿）：一周从周一开始，默认下周一至周日（今天是周一则本周）。
 * 按全家画像约束排 7 顿晚饭、不重样；正式的可编辑草稿与生成食集走 /mealplan/drafts（mealplan 上下文）。
 */
public class PlanWeekTool implements AgentTool {

    public static final String NAME = "plan_week";
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "排一周餐程";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "按家庭画像排一周（周一到周日）的晚饭草稿，不写入。weekStart 必须是周一。",
                schema(Map.of("weekStart", prop("string", "周一的日期 yyyy-MM-dd，默认下周一"),
                        "servings", prop("integer", "人数"),
                        "constraints", stringArray("额外要求，如 少盐、工作日30分钟内、宝宝辅食版"))));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        Kitchen k = ctx.kitchen();
        LocalDate today = k.now().toLocalDate();
        LocalDate start;
        String ws = str(args, "weekStart");
        try {
            start = ws == null ? (today.getDayOfWeek() == DayOfWeek.MONDAY ? today
                    : today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))) : LocalDate.parse(ws);
        } catch (RuntimeException e) {
            return ToolOutcome.fail("weekStart 格式应为 yyyy-MM-dd");
        }
        if (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            start = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        List<String> constraints = strings(args, "constraints");
        ProfileConstraintPolicy.Constraint family = ProfileConstraintPolicy.of(k.members());
        Set<String> exclude = new LinkedHashSet<>(family.excludeTags());
        // 宝宝单独做辅食版，不让宝宝约束卡掉全家的菜：只保留非宝宝成员的硬约束 + 用户明说的
        exclude.removeAll(List.of("辣", "生冷"));
        Integer maxMinutes = constraints.stream().anyMatch(c -> c.contains("30")) ? 30 : null;
        List<String> tags = constraints.stream().filter(c -> c.equals("少盐") || c.equals("控脂") || c.equals("素菜")).toList();
        List<DishInfo> pool = new ArrayList<>(k.searchDishes(new DishQuery(null, List.of(), tags, exclude,
                family.preferTags(), maxMinutes, 8)));
        if (pool.size() < 7) {
            for (DishInfo d : k.searchDishes(new DishQuery(null, List.of(), List.of(), exclude, family.preferTags(),
                    null, 8))) {
                if (pool.stream().noneMatch(x -> x.id() == d.id())) pool.add(d);
            }
        }
        if (pool.isEmpty()) return ToolOutcome.fail("没有符合约束的菜，排不出一周");
        List<Map<String, Object>> days = new ArrayList<>();
        List<DishInfo> picked = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            DishInfo d = pool.get(i % pool.size());
            picked.add(d);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", start.plusDays(i).toString());
            day.put("weekday", WEEKDAYS[i]);
            day.put("dinner", d.name());
            days.add(day);
        }
        String range = start.getMonthValue() + "." + start.getDayOfMonth() + "–"
                + start.plusDays(6).getMonthValue() + "." + start.plusDays(6).getDayOfMonth();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "plan");
        card.put("draftId", null);
        card.put("range", range);
        card.put("days", 7);
        card.put("weekStart", start.toString());
        card.put("items", picked.stream().map(ToolSupport::dishCard).toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekStart", start.toString());
        data.put("weekEnd", start.plusDays(6).toString());
        data.put("days", days);
        data.put("constraintsApplied", family.notes());
        data.put("note", "这是草稿，用户可在「一周餐程」里换菜后生成食集和采购清单");
        return ToolOutcome.ok("排好 " + range + "（周一到周日）7 顿晚饭草稿", data, List.of(card));
    }
}

package com.gudu.xsd.modules.mealplan.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;
import com.gudu.xsd.modules.mealplan.domain.port.MealPlanModelPort;
import com.gudu.xsd.modules.mealplan.domain.service.ArrangeContext;
import com.gudu.xsd.modules.mealplan.domain.service.CandidateFilter;
import com.gudu.xsd.modules.mealplan.domain.service.MealArranger;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM 排布（经 {@link MealPlanModelPort}，默认模型网关 CHAT · thinking low · JSON）：
 * 把过滤后的候选、约束、已固定的格子交给模型排，结果只接受「候选池内、未被排除、本周未用、时长合规」的菜；
 * 某天没排满就整天交给启发式补；模型失败或输出不可解析时整体退回启发式。之后照常走 PlanValidator 校验回环。
 */
@Slf4j
public class LlmMealArranger implements MealArranger {

    /** 交给模型的候选上限（按快到期命中、钠由低到高排序截断，控制提示词长度）。 */
    static final int MAX_CANDIDATES = 120;

    static final String SCHEMA = "{\"days\":[{\"date\":\"2026-10-19\",\"slots\":[{\"meal\":\"LUNCH\",\"dishId\":1}]}]}";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MealPlanModelPort model;
    private final MealArranger fallback;

    public LlmMealArranger(MealPlanModelPort model, MealArranger fallback) {
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public List<PlannedSlot> arrange(ArrangeContext ctx) {
        List<DishCandidate> offered = offered(ctx);
        if (offered.isEmpty()) return fallback.arrange(ctx);
        Map<LocalDate, List<PlannedSlot>> byDay;
        try {
            String raw = model.arrange(prompt(ctx, offered), SCHEMA);
            byDay = accept(parse(raw), ctx, offered);
        } catch (RuntimeException e) {
            log.warn("LLM 排餐失败，退回启发式: {}", e.getMessage());
            return fallback.arrange(ctx);
        }
        int mealsPerDay = ctx.request().meals().size();
        List<PlannedSlot> kept = new ArrayList<>();
        List<LocalDate> incomplete = new ArrayList<>();
        for (LocalDate d : ctx.days()) {
            List<PlannedSlot> ds = byDay.getOrDefault(d, List.of());
            if (ds.size() == mealsPerDay) kept.addAll(ds);
            else incomplete.add(d);
        }
        if (incomplete.isEmpty()) return kept;
        List<PlannedSlot> fixed = new ArrayList<>(ctx.fixed());
        fixed.addAll(kept);
        List<PlannedSlot> filled = fallback.arrange(new ArrangeContext(ctx.request(), ctx.profile(), ctx.pool(),
                ctx.expiringIngredientIds(), incomplete, fixed, ctx.excludedDishIds()));
        List<PlannedSlot> out = new ArrayList<>(kept);
        out.addAll(filled);
        out.sort(Comparator.comparing(PlannedSlot::date).thenComparing(PlannedSlot::meal));
        return out;
    }

    /** 可交给模型的候选：未排除、未在固定格里用过，按快到期命中数降序、钠升序截断。 */
    List<DishCandidate> offered(ArrangeContext ctx) {
        Set<Long> fixedIds = ctx.fixed().stream().map(PlannedSlot::dishId).collect(Collectors.toSet());
        return ctx.pool().stream()
                .filter(c -> !ctx.excludedDishIds().contains(c.dishId()) && !fixedIds.contains(c.dishId()))
                .filter(c -> c.kind() != DishKind.DESSERT)
                .sorted(Comparator.comparingLong((DishCandidate c) -> -expiringHits(c, ctx))
                        .thenComparingDouble(c -> c.perServing().sodiumMg())
                        .thenComparingLong(DishCandidate::dishId))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    String prompt(ArrangeContext ctx, List<DishCandidate> offered) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是家庭营养师，为一家人排餐。每个日期的每个餐次只排一道菜，用 dishId 引用下面的候选。\n");
        sb.append("要排的日期：");
        for (LocalDate d : ctx.days()) sb.append(d).append('(').append(WeekRange.weekdayLabel(d)).append(") ");
        sb.append("\n餐次：").append(ctx.request().meals().stream().map(Meal::name).collect(Collectors.joining(",")));
        List<String> rules = new ArrayList<>();
        rules.add("本周不重样，也不要用「已固定」里出现过的菜。");
        rules.add("每餐必须是能撑起一餐的正菜（荤菜/素菜/汤），不要只排粥、馒头、米饭等主食。");
        rules.add("同一天荤素搭配，不要两餐都是荤菜或都是素菜。");
        rules.add(String.format("每人每天两餐合计钠尽量不超过 %.0fmg（候选给出的是 1 人份钠）。",
                (double) Math.max(0, ctx.profile().sodiumLimitMg() - 300)));
        if (ctx.profile().hasBaby()) rules.add("家里有宝宝：每天至少一餐选 baby=1 的菜。");
        if (ctx.request().weekdayMaxMinutes() != null) {
            rules.add("周一到周五只选 minutes ≤ " + ctx.request().weekdayMaxMinutes() + " 的菜。");
        }
        if (ctx.request().preferExpiring()) rules.add("优先选 expiring>0 的菜（能用掉快到期食材），越早的日期越优先。");
        sb.append("\n规则：\n");
        for (int i = 0; i < rules.size(); i++) sb.append(i + 1).append(". ").append(rules.get(i)).append('\n');
        if (!ctx.fixed().isEmpty()) {
            sb.append("已固定：");
            for (PlannedSlot s : ctx.fixed()) sb.append(s.date()).append(' ').append(s.meal()).append('=').append(s.dishId()).append("; ");
            sb.append('\n');
        }
        sb.append("候选（dishId|菜名|类别|minutes|1人份钠mg|baby|expiring）：\n");
        for (DishCandidate c : offered) {
            sb.append(c.dishId()).append('|').append(c.name()).append('|').append(kindLabel(c.kind())).append('|')
                    .append(c.minutes() == null ? "?" : c.minutes()).append('|')
                    .append(Math.round(c.perServing().sodiumMg())).append('|')
                    .append(c.babyFriendly() ? 1 : 0).append('|').append(expiringHits(c, ctx)).append('\n');
        }
        sb.append("只输出 JSON，格式：").append(SCHEMA);
        return sb.toString();
    }

    static List<RawSlot> parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("模型输出为空");
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("模型输出不是 JSON");
        JsonNode root;
        try {
            root = JSON.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new IllegalArgumentException("模型输出 JSON 解析失败", e);
        }
        List<RawSlot> out = new ArrayList<>();
        for (JsonNode day : root.path("days")) {
            LocalDate date;
            try {
                date = LocalDate.parse(day.path("date").asText());
            } catch (RuntimeException e) {
                continue;
            }
            for (JsonNode slot : day.path("slots")) {
                Meal meal;
                try {
                    meal = Meal.of(slot.path("meal").asText());
                } catch (RuntimeException e) {
                    continue;
                }
                if (!slot.path("dishId").canConvertToLong()) continue;
                out.add(new RawSlot(date, meal, slot.path("dishId").asLong()));
            }
        }
        return out;
    }

    /** 过滤非法格子并补角标：日期/餐次在本次范围内、菜在候选里、未重复、时长合规；每天每餐只取第一个。 */
    Map<LocalDate, List<PlannedSlot>> accept(List<RawSlot> raw, ArrangeContext ctx, List<DishCandidate> offered) {
        Map<Long, DishCandidate> byId = new LinkedHashMap<>();
        offered.forEach(c -> byId.put(c.dishId(), c));
        Set<LocalDate> days = Set.copyOf(ctx.days());
        Set<Meal> meals = Set.copyOf(ctx.request().meals());
        Set<Long> used = new HashSet<>();
        ctx.fixed().forEach(s -> used.add(s.dishId()));
        Set<Long> expiringLeft = new HashSet<>(ctx.expiringIngredientIds());
        Map<LocalDate, List<PlannedSlot>> byDay = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        Map<LocalDate, Boolean> dayHasBaby = new LinkedHashMap<>();
        for (RawSlot r : raw.stream().sorted(Comparator.comparing(RawSlot::date).thenComparing(RawSlot::meal)).toList()) {
            DishCandidate c = byId.get(r.dishId());
            if (c == null || !days.contains(r.date()) || !meals.contains(r.meal())) continue;
            if (used.contains(c.dishId()) || !taken.add(r.date() + "/" + r.meal())) continue;
            if (!CandidateFilter.fitsTime(c, r.date(), ctx.request().weekdayMaxMinutes())) {
                taken.remove(r.date() + "/" + r.meal());
                continue;
            }
            Set<SlotBadge> badges = EnumSet.noneOf(SlotBadge.class);
            if (ctx.request().preferExpiring() && c.ingredientIds().stream().anyMatch(expiringLeft::contains)) {
                badges.add(SlotBadge.EXPIRING);
            }
            if (ctx.profile().hasBaby() && c.babyFriendly() && !dayHasBaby.getOrDefault(r.date(), false)) {
                badges.add(SlotBadge.BABY);
                dayHasBaby.put(r.date(), true);
            }
            used.add(c.dishId());
            expiringLeft.removeAll(c.ingredientIds());
            byDay.computeIfAbsent(r.date(), d -> new ArrayList<>()).add(new PlannedSlot(r.date(), r.meal(), c.dishId(), badges));
        }
        return byDay;
    }

    private static long expiringHits(DishCandidate c, ArrangeContext ctx) {
        if (!ctx.request().preferExpiring()) return 0;
        return c.ingredientIds().stream().filter(ctx.expiringIngredientIds()::contains).count();
    }

    private static String kindLabel(DishKind k) {
        return switch (k) {
            case MEAT -> "荤";
            case VEG -> "素";
            case SOUP -> "汤";
            case STAPLE -> "主食";
            case DESSERT -> "甜品";
        };
    }

    record RawSlot(LocalDate date, Meal meal, long dishId) {
    }
}

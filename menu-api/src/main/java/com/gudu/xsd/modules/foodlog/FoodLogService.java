package com.gudu.xsd.modules.foodlog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.review.Review;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 食记（做菜日记，V45）：从 cooking_record 自动生成——统计卡 + 时间轴 + 按菜汇总 + 年视图 + 单条详情。
 *
 * <p>一顿饭 = cooking_record 按 menu_id 分组（整集做）；menu_id null = 历史单菜直做（每条算一顿）。
 * 用材（用完/用了一些）从 cooking_record.memo 解析（格式 "用完:1,2;用了一些:3"，CookService.buildUsedMemo 写）。
 * 餐次按 cooked_at 推断：&lt;10 早餐 / 10~15 午餐 / 15~21 晚餐 / 其余加餐。
 */
@Service
@RequiredArgsConstructor
public class FoodLogService {

    public static final String MEAL_BREAKFAST = "breakfast";
    public static final String MEAL_LUNCH = "lunch";
    public static final String MEAL_DINNER = "dinner";
    public static final String MEAL_SNACK = "snack";

    private final CookingRecordMapper cookingRecordMapper;
    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final DishMapper dishMapper;
    private final IngredientMapper ingredientMapper;
    private final ReviewMapper reviewMapper;

    // ===================== 纯函数（可单测） =====================

    /** 餐次推断（按小时）。 */
    public static String mealTypeOf(int hour) {
        if (hour < 10) return MEAL_BREAKFAST;
        if (hour < 15) return MEAL_LUNCH;
        if (hour < 21) return MEAL_DINNER;
        return MEAL_SNACK;
    }

    /** 解析做菜确认 memo："用完:1,2;用了一些:3" → 食材 id 分组。 */
    public static UsedMemo parseUsedMemo(String memo) {
        List<Long> usedUp = new ArrayList<>();
        List<Long> partial = new ArrayList<>();
        if (memo == null || memo.isBlank()) return new UsedMemo(usedUp, partial);
        for (String part : memo.split(";")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            List<Long> ids = Arrays.stream(kv[1].split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Long.parseLong(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }).filter(Objects::nonNull).toList();
            if ("用完".equals(kv[0])) usedUp.addAll(ids);
            else if ("用了一些".equals(kv[0])) partial.addAll(ids);
        }
        return new UsedMemo(usedUp, partial);
    }

    /** 用材解析结果。 */
    public record UsedMemo(List<Long> usedUp, List<Long> partial) {}

    // ===================== 月视图：统计卡 + 时间轴 =====================

    public MonthVO month(Long memberId, int year, int month,
                         String meal, String source, Boolean reviewed,
                         int pageNum, int pageSize) {
        List<CookingRecord> records = cookingRecordMapper.selectList(
                new QueryWrapper<CookingRecord>()
                        .eq(memberId != null, "member_id", memberId)
                        .apply(month > 0
                                ? "YEAR(cooked_at) = {0} AND MONTH(cooked_at) = {1}"
                                : "YEAR(cooked_at) = {0}",
                                month > 0 ? new Object[]{year, month} : new Object[]{year})
                        .orderByAsc("cooked_at"));
        if (records.isEmpty()) {
            return new MonthVO(new Summary(0, 0, 0, List.of()), List.of(), 0);
        }

        // 一顿饭 = 按 menu_id 分组（null 每条单独一顿）
        Map<Long, List<CookingRecord>> byMenu = new LinkedHashMap<>();
        List<CookingRecord> standalone = new ArrayList<>();
        for (CookingRecord r : records) {
            if (r.getMenuId() != null) {
                byMenu.computeIfAbsent(r.getMenuId(), k -> new ArrayList<>()).add(r);
            } else {
                standalone.add(r);
            }
        }
        List<Meal> meals = new ArrayList<>();
        Map<Long, String> menuNames = menuNames(byMenu.keySet());
        Map<Long, String> dishNames = dishNames(records.stream().map(CookingRecord::getDishId).toList());
        Map<Long, String> ingNames = ingredientNames(allMemoIngredientIds(records));
        List<Long> reviewedMenuIds = reviewedMenuIds(byMenu.keySet());
        List<Long> reviewedDishIds = reviewedDishIds(standalone.stream().map(CookingRecord::getDishId).toList());

        for (Map.Entry<Long, List<CookingRecord>> e : byMenu.entrySet()) {
            Meal m = toMeal(e.getKey(), menuNames.get(e.getKey()), e.getValue(), dishNames, ingNames,
                    reviewedMenuIds.contains(e.getKey()));
            if (matchFilter(m, meal, source, reviewed)) meals.add(m);
        }
        for (CookingRecord r : standalone) {
            Meal m = toMeal(null, dishNames.get(r.getDishId()), List.of(r), dishNames, ingNames,
                    reviewedDishIds.contains(r.getDishId()));
            if (matchFilter(m, meal, source, reviewed)) meals.add(m);
        }

        Summary summary = buildSummary(records, dishNames, meal, source, reviewed);
        // 时间轴分页（内存切片：分组在内存完成，SQL 分页会破坏「一顿饭」分组）
        int total = meals.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<Meal> page = from >= total ? List.of() : meals.subList(from, to);
        return new MonthVO(summary, page, total);
    }

    /** 组装一顿饭（整集做：取组内第一条 memo 解析用材；单菜直做：直接解析）。 */
    private Meal toMeal(Long menuId, String name, List<CookingRecord> group,
                        Map<Long, String> dishNames, Map<Long, String> ingNames, boolean reviewed) {
        CookingRecord first = group.get(0);
        UsedMemo memo = parseUsedMemo(first.getMemo());
        List<String> names = group.stream()
                .map(r -> dishNames.getOrDefault(r.getDishId(), "菜 #" + r.getDishId()))
                .toList();
        return new Meal(menuId, name == null ? "单菜直做" : name, first.getCookedAt(),
                group.size(), null, names, memo.usedUp().size(), memo.partial().size(), reviewed);
    }

    private boolean matchFilter(Meal m, String meal, String source, Boolean reviewed) {
        if (meal != null && !meal.isBlank()
                && !mealTypeOf(m.cookedAt().getHour()).equals(meal)) return false;
        if (source != null && !source.isBlank()) {
            boolean isMenu = m.menuId() != null;
            if ("menu".equals(source) && !isMenu) return false;
            if ("dish".equals(source) && isMenu) return false;
        }
        if (reviewed != null && m.reviewed() != reviewed) return false;
        return true;
    }

    private Summary buildSummary(List<CookingRecord> records, Map<Long, String> dishNames,
                                 String meal, String source, Boolean reviewed) {
        // 筛选只影响时间轴展示，统计卡按全月口径（原型未定义筛选下统计，取全月）
        int dishes = records.size();
        int cookDays = (int) records.stream()
                .map(r -> r.getCookedAt().toLocalDate()).distinct().count();
        Map<Long, Long> byDish = records.stream()
                .filter(r -> r.getDishId() != null)
                .collect(Collectors.groupingBy(CookingRecord::getDishId, Collectors.counting()));
        List<String> top = byDish.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> dishNames.getOrDefault(e.getKey(), "菜 #" + e.getKey()))
                .toList();
        // 顿饭数：整集每组 1 顿 + 单菜直做每条 1 顿
        long menuGroups = records.stream().map(CookingRecord::getMenuId)
                .filter(Objects::nonNull).distinct().count();
        long standalone = records.stream().filter(r -> r.getMenuId() == null).count();
        int meals = (int) (menuGroups + standalone);
        return new Summary(meals, dishes, cookDays, top);
    }

    // ===================== 按菜汇总 =====================

    public ByDishVO byDish(Long memberId, int year, int month,
                           String meal, String source, Boolean reviewed) {
        List<CookingRecord> records = cookingRecordMapper.selectList(
                new QueryWrapper<CookingRecord>()
                        .eq(memberId != null, "member_id", memberId)
                        .apply(month > 0
                                ? "YEAR(cooked_at) = {0} AND MONTH(cooked_at) = {1}"
                                : "YEAR(cooked_at) = {0}",
                                month > 0 ? new Object[]{year, month} : new Object[]{year}));
        if (records.isEmpty()) return new ByDishVO(0, List.of());

        // 先按一顿饭的筛选条件过滤记录
        Map<Long, List<CookingRecord>> byMenu = new LinkedHashMap<>();
        List<CookingRecord> standalone = new ArrayList<>();
        for (CookingRecord r : records) {
            if (r.getMenuId() != null) byMenu.computeIfAbsent(r.getMenuId(), k -> new ArrayList<>()).add(r);
            else standalone.add(r);
        }
        Map<Long, String> menuNames = menuNames(byMenu.keySet());
        Map<Long, String> dishNames = dishNames(records.stream().map(CookingRecord::getDishId).toList());
        Map<Long, String> ingNames = Map.of();
        List<Long> reviewedMenuIds = reviewedMenuIds(byMenu.keySet());
        List<Long> reviewedDishIds = reviewedDishIds(standalone.stream().map(CookingRecord::getDishId).toList());
        List<CookingRecord> filtered = new ArrayList<>();
        for (Map.Entry<Long, List<CookingRecord>> e : byMenu.entrySet()) {
            Meal m = toMeal(e.getKey(), menuNames.get(e.getKey()), e.getValue(), dishNames, ingNames,
                    reviewedMenuIds.contains(e.getKey()));
            if (matchFilter(m, meal, source, reviewed)) filtered.addAll(e.getValue());
        }
        for (CookingRecord r : standalone) {
            Meal m = toMeal(null, dishNames.get(r.getDishId()), List.of(r), dishNames, ingNames,
                    reviewedDishIds.contains(r.getDishId()));
            if (matchFilter(m, meal, source, reviewed)) filtered.add(r);
        }

        // 按菜聚合：次数 / 最近时间 / ★均分
        Map<Long, List<CookingRecord>> byDish = filtered.stream()
                .filter(r -> r.getDishId() != null)
                .collect(Collectors.groupingBy(CookingRecord::getDishId));
        Map<Long, Double> avgStars = dishAvgStars(byDish.keySet());
        List<Item> items = new ArrayList<>();
        for (Map.Entry<Long, List<CookingRecord>> e : byDish.entrySet()) {
            Long dishId = e.getKey();
            List<CookingRecord> list = e.getValue();
            LocalDateTime last = list.stream().map(CookingRecord::getCookedAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            Double avg = avgStars.get(dishId);
            if (reviewed != null) {
                boolean has = avg != null;
                if (has != reviewed) continue;
            }
            items.add(new Item(dishId, dishNames.getOrDefault(dishId, "菜 #" + dishId),
                    list.size(), last, avg));
        }
        items.sort(Comparator.comparingInt(Item::count).reversed()
                .thenComparing(it -> it.lastCookedAt() == null ? LocalDateTime.MIN : it.lastCookedAt(),
                        Comparator.reverseOrder()));
        return new ByDishVO(items.size(), items);
    }

    // ===================== 年视图 =====================

    public YearVO year(Long memberId, int year) {
        List<CookingRecord> records = cookingRecordMapper.selectList(
                new QueryWrapper<CookingRecord>()
                        .eq(memberId != null, "member_id", memberId)
                        .apply("YEAR(cooked_at) = {0}", year));
        int[] months = new int[12];
        for (CookingRecord r : records) {
            if (r.getCookedAt() != null) {
                months[r.getCookedAt().getMonthValue() - 1]++;
            }
        }
        return new YearVO(year, months);
    }

    // ===================== 单条详情 =====================

    public DetailVO detail(Long memberId, Long menuId) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) throw new BizException("食集不存在");
        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", menuId).orderByAsc("id"));
        Map<Long, String> dishNames = dishNames(mds.stream().map(MenuDish::getDishId).toList());
        List<DishItem> dishes = mds.stream().map(d -> new DishItem(d.getDishId(),
                dishNames.getOrDefault(d.getDishId(), "菜 #" + d.getDishId()),
                d.getServingFactor(), d.getNote())).toList();

        // 用材：该食集最新一条 cooking_record 的 memo
        List<CookingRecord> recs = cookingRecordMapper.selectList(
                new QueryWrapper<CookingRecord>().eq("menu_id", menuId)
                        .orderByDesc("cooked_at").last("LIMIT 1"));
        UsedMemo memo = recs.isEmpty() ? new UsedMemo(List.of(), List.of())
                : parseUsedMemo(recs.get(0).getMemo());
        Map<Long, String> ingNames = ingredientNames(concat(memo.usedUp(), memo.partial()));
        List<String> usedUp = memo.usedUp().stream()
                .map(id -> ingNames.getOrDefault(id, "食材 #" + id)).toList();
        List<String> partial = memo.partial().stream()
                .map(id -> ingNames.getOrDefault(id, "食材 #" + id)).toList();

        boolean reviewed = reviewedMenuIds(List.of(menuId)).contains(menuId);
        LocalDateTime cookedAt = recs.isEmpty() ? null : recs.get(0).getCookedAt();
        return new DetailVO(menuId, menu.getName(), cookedAt, menu.getServingCount(),
                dishes, usedUp, partial, reviewed);
    }

    // ===================== 内部辅助 =====================

    private Map<Long, String> menuNames(java.util.Collection<Long> ids) {
        List<Long> valid = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return menuMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(Menu::getId, Menu::getName, (a, b) -> a));
    }

    private Map<Long, String> dishNames(java.util.Collection<Long> ids) {
        List<Long> valid = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return dishMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(Dish::getId, Dish::getName, (a, b) -> a));
    }

    private Map<Long, String> ingredientNames(java.util.Collection<Long> ids) {
        List<Long> valid = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return ingredientMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
    }

    private List<Long> allMemoIngredientIds(List<CookingRecord> records) {
        List<Long> ids = new ArrayList<>();
        for (CookingRecord r : records) {
            UsedMemo m = parseUsedMemo(r.getMemo());
            ids.addAll(m.usedUp());
            ids.addAll(m.partial());
        }
        return ids;
    }

    private List<Long> reviewedMenuIds(java.util.Collection<Long> menuIds) {
        List<Long> valid = menuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return List.of();
        return reviewMapper.selectList(new QueryWrapper<Review>()
                        .in("menu_id", valid).isNull("dish_id").select("menu_id"))
                .stream().map(Review::getMenuId).distinct().toList();
    }

    private List<Long> reviewedDishIds(java.util.Collection<Long> dishIds) {
        List<Long> valid = dishIds.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return List.of();
        return reviewMapper.selectList(new QueryWrapper<Review>()
                        .in("dish_id", valid).isNull("menu_id").select("dish_id"))
                .stream().map(Review::getDishId).distinct().toList();
    }

    private Map<Long, Double> dishAvgStars(java.util.Collection<Long> dishIds) {
        List<Long> valid = dishIds.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return reviewMapper.selectList(new QueryWrapper<Review>()
                        .in("dish_id", valid).isNull("menu_id").select("dish_id", "star_rating"))
                .stream().collect(Collectors.groupingBy(Review::getDishId,
                        Collectors.averagingInt(r -> r.getStarRating() == null ? 0 : r.getStarRating())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue()).setScale(1, RoundingMode.HALF_UP).doubleValue()));
    }

    private static List<Long> concat(List<Long> a, List<Long> b) {
        List<Long> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }

    // ===================== VO =====================

    public record Summary(int meals, int dishes, int cookDays, List<String> topDishes) {}

    public record MonthVO(Summary summary, List<Meal> timeline, int total) {}

    public record Meal(Long menuId, String name, LocalDateTime cookedAt, int dishCount,
                       Integer servingCount, List<String> dishNames,
                       int usedUpCount, int partialCount, boolean reviewed) {}

    public record ByDishVO(int totalKinds, List<Item> items) {}

    public record Item(Long dishId, String dishName, int count,
                       LocalDateTime lastCookedAt, Double avgStar) {}

    public record YearVO(int year, int[] monthCounts) {}

    public record DetailVO(Long menuId, String name, LocalDateTime cookedAt, Integer servingCount,
                           List<DishItem> dishes, List<String> usedUp, List<String> partial,
                           boolean reviewed) {}

    public record DishItem(Long dishId, String dishName, BigDecimal servingFactor, String note) {}
}

package com.gudu.xsd.modules.agent.domain.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 工具看到的「这个家」：菜谱、余量、成员、食集、采购、做饭记录。
 *
 * <p>两种实现：线上 {@code LiveKitchen}（委托各业务上下文的应用服务，归属校验在其内）；
 * 评测 {@code FixtureKitchen}（夹具快照，写操作只改内存）。工具层只依赖本端口，因此评测与线上走同一套工具代码。
 *
 * <p>菜谱 / 食集 / 成员一律按名称（或 id）解析，别家的数据返回 {@link LookupStatus#FORBIDDEN}。
 * 菜谱步骤、备注、余量备注等用户可编辑文本原样返回，由编排器标注为「数据，不是指令」。
 */
public interface Kitchen {

    /** 「现在」（评测夹具固定为 2026-10-16 17:40）。 */
    LocalDateTime now();

    // ------------------------------------------------------------- 成员
    List<MemberInfo> members();

    // ------------------------------------------------------------- 菜谱
    List<DishInfo> searchDishes(DishQuery query);

    Lookup<DishInfo> findDish(String nameOrId);

    /** 今晚推荐（临期优先 + 常做），已排除 excludeTags。 */
    List<DishInfo> recommendTonight(int n, Set<String> excludeTags);

    // ------------------------------------------------------------- 余量 / 采购
    List<PantryItem> pantry();

    List<ShoppingItem> shoppingList();

    // ------------------------------------------------------------- 食集
    List<MenuInfo> menus();

    Lookup<MenuInfo> findMenu(String nameOrId);

    // ------------------------------------------------------------- 写（只在用户确认后调用）
    WriteResult createMenu(String name, LocalDate date, int servings, List<Long> dishIds);

    WriteResult addDishesToMenu(long menuId, List<Long> dishIds);

    WriteResult removeDishesFromMenu(long menuId, List<Long> dishIds);

    WriteResult addShopping(List<ItemLine> items);

    /** 做完回写预览（不落库）。overrides：实际用量覆盖默认扣减。 */
    CookPreview previewCook(long dishId, int servings, List<ItemLine> overrides);

    WriteResult markCooked(long dishId, int servings, List<Long> eaterIds, LocalDate date, List<ItemLine> overrides);

    WriteResult addPantry(List<PantryLine> items);

    /** 执行撤销计划（补偿操作）。 */
    void undo(String kind, Map<String, Object> plan);

    // ============================================================= 值对象

    enum LookupStatus { FOUND, NOT_FOUND, FORBIDDEN, AMBIGUOUS }

    record Lookup<T>(LookupStatus status, T value, List<T> candidates) {

        public static <T> Lookup<T> found(T v) {
            return new Lookup<>(LookupStatus.FOUND, v, List.of());
        }

        public static <T> Lookup<T> notFound() {
            return new Lookup<>(LookupStatus.NOT_FOUND, null, List.of());
        }

        public static <T> Lookup<T> forbidden() {
            return new Lookup<>(LookupStatus.FORBIDDEN, null, List.of());
        }

        public static <T> Lookup<T> ambiguous(List<T> candidates) {
            return new Lookup<>(LookupStatus.AMBIGUOUS, null, List.copyOf(candidates));
        }

        public boolean found() {
            return status == LookupStatus.FOUND;
        }
    }

    /**
     * @param tags 档案标签（高血压 / 高血脂 / 宝宝辅食），以及 {@code avoid:辣}、{@code prefer:少盐} 形式的显式约束
     */
    record MemberInfo(long id, String name, boolean me, Integer age, List<String> tags, String avatarUrl) {
        public MemberInfo {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    record IngredientLine(String name, String amount, boolean condiment, String iconKey) {
    }

    record Coverage(int have, int total, List<String> missing) {
        public Coverage {
            missing = missing == null ? List.of() : List.copyOf(missing);
        }
    }

    /**
     * @param difficulty  简单 / 中等 / 较难
     * @param sodiumMg    每份钠估算（可空）
     * @param description 菜谱简介（用户可编辑文本）
     */
    record DishInfo(long id, String name, String coverUrl, Integer minutes, String difficulty, List<String> tags,
                    List<IngredientLine> ingredients, Integer sodiumMg, Coverage coverage, List<String> steps,
                    String description) {
        public DishInfo {
            tags = tags == null ? List.of() : List.copyOf(tags);
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    /**
     * @param excludeTags 硬过滤（含服务端合并的画像约束）
     * @param preferTags  软偏好（排序加权）
     */
    record DishQuery(String query, List<String> ingredients, List<String> tags, Set<String> excludeTags,
                     Set<String> preferTags, Integer maxMinutes, int limit) {
        public DishQuery {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            tags = tags == null ? List.of() : List.copyOf(tags);
            excludeTags = excludeTags == null ? Set.of() : Set.copyOf(excludeTags);
            preferTags = preferTags == null ? Set.of() : Set.copyOf(preferTags);
            limit = limit <= 0 ? 3 : Math.min(limit, 8);
        }
    }

    /** @param status ENOUGH / LOW / OUT；note 为用户备注 */
    record PantryItem(Long ingredientId, String name, String iconKey, String status, Integer daysLeft, String amount,
                      LocalDate bestBefore, String note) {
    }

    record ShoppingItem(String name, String amount, String unit, String forMenu, boolean checked) {
    }

    /** @param status ONGOING / UPCOMING / DONE；note 为用户备注 */
    record MenuInfo(long id, String name, LocalDate date, int servings, String status, List<String> dishNames,
                    List<String> coverUrls, String note) {
        public MenuInfo {
            dishNames = dishNames == null ? List.of() : List.copyOf(dishNames);
            coverUrls = coverUrls == null ? List.of() : List.copyOf(coverUrls);
        }
    }

    /** 名称 + 数量 + 单位（采购项 / 用量覆盖）。 */
    record ItemLine(String name, String amount, String unit) {
    }

    record PantryLine(String name, String amount, String unit, LocalDate bestBefore) {
    }

    /** @param status USED_UP / LEFT / SKIP（调料不扣） */
    record CookLine(Long ingredientId, String name, String iconKey, String delta, String after, String status) {
    }

    record CookPreview(DishInfo dish, int servings, List<CookLine> lines) {
        public CookPreview {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * 写操作结果。
     *
     * @param result   返回给客户端 / 模型的结果
     * @param undoKind 撤销类型（null = 不可撤销）
     * @param undoPlan 撤销计划
     */
    record WriteResult(Map<String, Object> result, String undoKind, Map<String, Object> undoPlan) {
        public WriteResult {
            result = result == null ? Map.of() : Map.copyOf(result);
            undoPlan = undoPlan == null ? Map.of() : Map.copyOf(undoPlan);
        }
    }
}

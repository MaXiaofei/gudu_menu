package com.gudu.xsd.modules.agent.application.assess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评测沙箱（FixtureToolSandbox）：读 {@code agent-eval/fixtures/*.json} 的家庭快照，写操作只改内存副本，
 * 不碰数据库。每条用例一个新实例，{@link #writes()} 统计确认前后的写入次数，{@link #state()} 供 effects 断言。
 *
 * <p>夹具结构见 agent-eval/README.md：family.{members, pantry, menus, shoppingList, cookRecords, favorites}、
 * recipes[]（public）、otherFamilies[].{menus, pantry, privateRecipes}（越权用例：一律 FORBIDDEN）。
 * id：本家菜谱 / 食集按顺序编号 1..n，别家从 9001 起；夹具里的字符串 id（menu_xw_tonight）同样可解析。
 */
public class FixtureKitchen implements Kitchen {

    static final long FOREIGN_BASE = 9000;
    /** 用量覆盖的特殊值：扣到 0 / 不扣（确认卡上改「用完了 / 没用」）。 */
    public static final String OVERRIDE_ALL = "ALL";
    public static final String OVERRIDE_SKIP = "0";

    private final ObjectMapper om;
    private final LocalDateTime now;
    private Map<String, Object> family;
    private final List<Map<String, Object>> recipes;
    private final List<Map<String, Object>> foreignRecipes = new ArrayList<>();
    private final List<Map<String, Object>> foreignMenus = new ArrayList<>();
    private final Set<String> foreignPantry = new LinkedHashSet<>();
    private int writes;

    @SuppressWarnings("unchecked")
    public FixtureKitchen(Map<String, Object> fixture, ObjectMapper om) {
        this.om = om;
        Map<String, Object> copy = deepCopy(fixture);
        this.now = parseNow(String.valueOf(copy.get("now")), String.valueOf(copy.get("today")));
        this.family = (Map<String, Object>) copy.get("family");
        this.recipes = list(copy.get("recipes"));
        for (Map<String, Object> other : list(copy.get("otherFamilies"))) {
            foreignRecipes.addAll(list(other.get("privateRecipes")));
            foreignMenus.addAll(list(other.get("menus")));
            for (Map<String, Object> p : list(other.get("pantry"))) foreignPantry.add(String.valueOf(p.get("name")));
        }
    }

    // ================================================================= 夹具补丁

    /**
     * 应用 fixturePatch：pantry.upsert/remove/replace、recipes.patch.&lt;菜名&gt;、menus.patch.&lt;食集名&gt;。
     * toolErrors / gateway / quota 由运行器读取。
     */
    @SuppressWarnings("unchecked")
    public void applyPatch(Map<String, Object> patch) {
        if (patch == null) return;
        Map<String, Object> pantry = (Map<String, Object>) patch.get("pantry");
        if (pantry != null) {
            if (pantry.get("replace") != null) family.put("pantry", new ArrayList<>(list(pantry.get("replace"))));
            for (Object n : pantry.get("remove") instanceof Collection<?> c ? c : List.of()) {
                pantryList().removeIf(p -> String.valueOf(n).equals(p.get("name")));
            }
            for (Map<String, Object> up : list(pantry.get("upsert"))) {
                Map<String, Object> cur = pantryItem(String.valueOf(up.get("name")));
                if (cur == null) {
                    Map<String, Object> n = new LinkedHashMap<>(up);
                    n.putIfAbsent("status", "ENOUGH");
                    pantryList().add(n);
                } else {
                    cur.putAll(up);
                }
            }
        }
        Map<String, Object> rp = (Map<String, Object>) ((Map<String, Object>) patch.getOrDefault("recipes", Map.of()))
                .getOrDefault("patch", Map.of());
        rp.forEach((name, fields) -> recipes.stream().filter(r -> name.equals(r.get("name"))).findFirst()
                .ifPresent(r -> r.putAll((Map<String, Object>) fields)));
        Map<String, Object> mp = (Map<String, Object>) ((Map<String, Object>) patch.getOrDefault("menus", Map.of()))
                .getOrDefault("patch", Map.of());
        mp.forEach((name, fields) -> menuList().stream().filter(m -> name.equals(m.get("name"))).findFirst()
                .ifPresent(m -> m.putAll((Map<String, Object>) fields)));
    }

    // ================================================================= 读

    @Override
    public LocalDateTime now() {
        return now;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MemberInfo> members() {
        List<MemberInfo> out = new ArrayList<>();
        for (Map<String, Object> m : list(family.get("members"))) {
            List<String> tags = new ArrayList<>(strings(m.get("profiles")));
            Map<String, Object> c = m.get("constraints") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
            strings(c.get("avoidTags")).forEach(t -> tags.add("avoid:" + t));
            strings(c.get("preferTags")).forEach(t -> tags.add("prefer:" + t));
            out.add(new MemberInfo(num(m.get("memberId")).longValue(), String.valueOf(m.get("name")),
                    Boolean.TRUE.equals(m.get("isMe")), m.get("age") == null ? null : num(m.get("age")).intValue(),
                    tags, null));
        }
        return out;
    }

    @Override
    public List<DishInfo> searchDishes(DishQuery q) {
        List<Map<String, Object>> pool = new ArrayList<>(recipes);
        if (!q.ingredients().isEmpty()) {
            pool.removeIf(r -> ingredientNames(r).stream().noneMatch(n -> q.ingredients().stream()
                    .anyMatch(i -> sameFood(n, i))));
        }
        List<Map<String, Object>> byQuery = q.query() == null ? List.of() : pool.stream()
                .filter(r -> matchesQuery(r, q.query())).toList();
        if (!byQuery.isEmpty()) pool = new ArrayList<>(byQuery);
        pool.removeIf(r -> tags(r).stream().anyMatch(q.excludeTags()::contains));
        pool.removeIf(r -> !tags(r).containsAll(q.tags()));
        if (q.maxMinutes() != null) pool.removeIf(r -> r.get("minutes") != null && num(r.get("minutes")).intValue() > q.maxMinutes());
        Set<String> expiring = expiringNames();
        pool.sort(Comparator.comparingDouble((Map<String, Object> r) -> -score(r, q.preferTags(), expiring))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        return pool.stream().limit(q.limit()).map(this::info).toList();
    }

    private double score(Map<String, Object> r, Set<String> prefer, Set<String> expiring) {
        Coverage c = coverage(r);
        double s = c.total() == 0 ? 0 : 2.0 * c.have() / c.total();
        for (String t : tags(r)) if (prefer.contains(t)) s += 0.5;
        for (String n : ingredientNames(r)) if (expiring.contains(n)) s += 1;
        if (strings(family.get("favorites")).contains(String.valueOf(r.get("name")))) s += 0.3;
        return s;
    }

    private static boolean matchesQuery(Map<String, Object> r, String query) {
        String name = String.valueOf(r.get("name"));
        String q = query.trim();
        if (q.isEmpty()) return false;
        if (name.contains(q) || q.contains(name)) return true;
        for (String t : tags(r)) if (q.contains(t)) return true;
        for (String n : ingredientNames(r)) if (q.contains(n)) return true;
        return false;
    }

    @Override
    public Lookup<DishInfo> findDish(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Lookup.notFound();
        String ref = nameOrId.trim();
        if (ref.matches("\\d+")) {
            long id = Long.parseLong(ref);
            if (id >= 1 && id <= recipes.size()) return Lookup.found(info(recipes.get((int) id - 1)));
            return id > FOREIGN_BASE ? Lookup.forbidden() : Lookup.notFound();
        }
        for (Map<String, Object> r : recipes) if (ref.equals(r.get("name"))) return Lookup.found(info(r));
        for (Map<String, Object> r : foreignRecipes) if (ref.equals(r.get("name"))) return Lookup.forbidden();
        List<Map<String, Object>> like = recipes.stream().filter(r -> {
            String n = String.valueOf(r.get("name"));
            return n.contains(ref) || (ref.length() >= 3 && ref.contains(n));
        }).toList();
        if (like.size() == 1) return Lookup.found(info(like.get(0)));
        if (like.size() > 1) return Lookup.ambiguous(like.stream().map(this::info).toList());
        List<Map<String, Object>> fuzzy = recipes.stream()
                .filter(r -> ref.length() >= 3 && editDistance(ref, String.valueOf(r.get("name"))) <= 1).toList();
        if (fuzzy.size() == 1) return Lookup.found(info(fuzzy.get(0)));
        return Lookup.notFound();
    }

    @Override
    public List<DishInfo> recommendTonight(int n, Set<String> excludeTags) {
        return searchDishes(new DishQuery(null, List.of(), List.of(), excludeTags, Set.of(), null, n));
    }

    @Override
    public List<PantryItem> pantry() {
        List<PantryItem> out = new ArrayList<>();
        long i = 1;
        for (Map<String, Object> p : pantryList()) {
            LocalDate bb = p.get("bestBefore") == null ? null : LocalDate.parse(String.valueOf(p.get("bestBefore")));
            Integer days = null;
            if (bb != null) days = (int) (bb.toEpochDay() - now.toLocalDate().toEpochDay());
            else if (p.get("daysLeft") != null) days = num(p.get("daysLeft")).intValue();
            out.add(new PantryItem(i++, String.valueOf(p.get("name")), null, String.valueOf(p.getOrDefault("status", "ENOUGH")),
                    days, amountText(p), bb, p.get("note") == null ? null : String.valueOf(p.get("note"))));
        }
        return out;
    }

    @Override
    public List<ShoppingItem> shoppingList() {
        return list(family.get("shoppingList")).stream().map(s -> new ShoppingItem(String.valueOf(s.get("name")),
                s.get("amount") == null ? null : plain(s.get("amount")), s.get("unit") == null ? null : String.valueOf(s.get("unit")),
                s.get("forMenu") == null ? null : String.valueOf(s.get("forMenu")), Boolean.TRUE.equals(s.get("checked"))))
                .toList();
    }

    @Override
    public List<MenuInfo> menus() {
        List<MenuInfo> out = new ArrayList<>();
        List<Map<String, Object>> ms = menuList();
        for (int i = 0; i < ms.size(); i++) out.add(menu(ms.get(i), i + 1));
        return out;
    }

    @Override
    public Lookup<MenuInfo> findMenu(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Lookup.notFound();
        String ref = nameOrId.trim();
        List<MenuInfo> mine = menus();
        List<Map<String, Object>> raw = menuList();
        for (int i = 0; i < raw.size(); i++) {
            if (ref.equals(raw.get(i).get("menuId")) || ref.equals(String.valueOf(i + 1))) return Lookup.found(mine.get(i));
        }
        for (MenuInfo m : mine) if (m.name().equals(ref)) return Lookup.found(m);
        for (Map<String, Object> f : foreignMenus) {
            if (ref.equals(f.get("menuId")) || ref.equals(f.get("name"))) return Lookup.forbidden();
        }
        if (ref.matches("\\d+") && Long.parseLong(ref) > FOREIGN_BASE) return Lookup.forbidden();
        List<MenuInfo> like = mine.stream().filter(m -> !"DONE".equals(m.status()))
                .filter(m -> m.name().contains(ref) || ref.contains(m.name())
                        || (ref.length() >= 2 && m.name().replace(" · ", "").contains(ref.replace(" · ", ""))))
                .toList();
        if (like.size() == 1) return Lookup.found(like.get(0));
        if (like.size() > 1) return Lookup.ambiguous(like);
        return Lookup.notFound();
    }

    // ================================================================= 写

    @Override
    public WriteResult createMenu(String name, LocalDate date, int servings, List<Long> dishIds) {
        Map<String, Object> snap = snapshot();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("menuId", "menu_new_" + (menuList().size() + 1));
        m.put("name", name);
        m.put("date", date.toString());
        m.put("status", date.isAfter(now.toLocalDate()) ? "UPCOMING" : "ONGOING");
        m.put("servings", servings);
        m.put("dishes", new ArrayList<>(dishIds.stream().map(this::dishName).toList()));
        menuList().add(m);
        writes++;
        return new WriteResult(Map.of("name", name, "added", dishIds.size()), "snapshot", snap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public WriteResult addDishesToMenu(long menuId, List<Long> dishIds) {
        Map<String, Object> snap = snapshot();
        Map<String, Object> m = menuList().get((int) menuId - 1);
        List<Object> dishes = (List<Object>) m.computeIfAbsent("dishes", k -> new ArrayList<>());
        int added = 0;
        for (Long id : dishIds) {
            String n = dishName(id);
            if (!dishes.contains(n)) {
                dishes.add(n);
                added++;
            }
        }
        writes++;
        return new WriteResult(Map.of("menu", m.get("name"), "added", added), "snapshot", snap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public WriteResult removeDishesFromMenu(long menuId, List<Long> dishIds) {
        Map<String, Object> snap = snapshot();
        Map<String, Object> m = menuList().get((int) menuId - 1);
        List<Object> dishes = (List<Object>) m.computeIfAbsent("dishes", k -> new ArrayList<>());
        int before = dishes.size();
        for (Long id : dishIds) dishes.remove(dishName(id));
        writes++;
        return new WriteResult(Map.of("menu", m.get("name"), "removed", before - dishes.size()), "snapshot", snap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public WriteResult addShopping(List<ItemLine> items) {
        Map<String, Object> snap = snapshot();
        List<Object> list = (List<Object>) family.computeIfAbsent("shoppingList", k -> new ArrayList<>());
        for (ItemLine it : items) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", it.name());
            if (it.amount() != null) s.put("amount", numOrText(it.amount()));
            if (it.unit() != null) s.put("unit", it.unit());
            s.put("checked", false);
            list.add(s);
        }
        writes++;
        return new WriteResult(Map.of("added", items.size()), "snapshot", snap);
    }

    @Override
    public CookPreview previewCook(long dishId, int servings, List<ItemLine> overrides) {
        Map<String, Object> r = recipes.get((int) dishId - 1);
        return new CookPreview(info(r), servings, cookLines(r, servings, overrides, false));
    }

    @Override
    @SuppressWarnings("unchecked")
    public WriteResult markCooked(long dishId, int servings, List<Long> eaterIds, LocalDate date, List<ItemLine> overrides) {
        Map<String, Object> snap = snapshot();
        Map<String, Object> r = recipes.get((int) dishId - 1);
        cookLines(r, servings, overrides, true);
        List<Object> records = (List<Object>) family.computeIfAbsent("cookRecords", k -> new ArrayList<>());
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("date", (date == null ? now.toLocalDate() : date).toString());
        rec.put("dish", r.get("name"));
        rec.put("servings", servings);
        rec.put("eaters", eaterIds.stream().map(this::memberName).toList());
        records.add(rec);
        writes++;
        return new WriteResult(Map.of("dish", r.get("name"), "servings", servings), "snapshot", snap);
    }

    @Override
    public WriteResult addPantry(List<PantryLine> items) {
        Map<String, Object> snap = snapshot();
        for (PantryLine it : items) {
            Map<String, Object> cur = pantryItem(it.name());
            if (cur == null) {
                cur = new LinkedHashMap<>();
                cur.put("name", it.name());
                pantryList().add(cur);
                cur.put("amount", it.amount() == null ? 1 : numOrText(it.amount()));
                if (it.unit() != null) cur.put("unit", it.unit());
            } else {
                BigDecimal a = dec(cur.get("amount"));
                BigDecimal add = dec(it.amount());
                boolean sameUnit = it.unit() == null || it.unit().equals(cur.get("unit"));
                if (a != null && add != null && sameUnit) cur.put("amount", plainNum(a.add(add)));
                else if (it.amount() != null) {
                    cur.put("amount", numOrText(it.amount()));
                    if (it.unit() != null) cur.put("unit", it.unit());
                }
            }
            cur.put("status", "ENOUGH");
            if (it.bestBefore() != null) {
                cur.put("bestBefore", it.bestBefore().toString());
                cur.put("daysLeft", (int) (it.bestBefore().toEpochDay() - now.toLocalDate().toEpochDay()));
            }
        }
        writes++;
        return new WriteResult(Map.of("added", items.size()), "snapshot", snap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void undo(String kind, Map<String, Object> plan) {
        Object fam = plan.get("family");
        if (fam instanceof Map<?, ?> m) {
            this.family = deepCopy((Map<String, Object>) m);
            writes++;
        }
    }

    // ================================================================= 评测用

    /** 已执行的写操作次数（含撤销）。 */
    public int writes() {
        return writes;
    }

    /** 当前家庭状态深拷贝（effects 断言 / noChange 比较）。 */
    public Map<String, Object> state() {
        return deepCopy(family);
    }

    /** effects 断言取值：entity = menu / pantry / shopping；返回 field 的值（实体不存在返回 null）。 */
    public Object effectValue(String entity, String name, String field) {
        List<Map<String, Object>> rows = switch (entity) {
            case "menu" -> menuList();
            case "pantry" -> pantryList();
            case "shopping" -> list(family.get("shoppingList"));
            case "cookRecord", "cooked" -> list(family.get("cookRecords"));
            default -> List.of();
        };
        for (Map<String, Object> r : rows) {
            Object n = r.containsKey("name") ? r.get("name") : r.get("dish");
            if (name == null || name.equals(n)) return field == null ? r : r.get(field);
        }
        return null;
    }

    /** 菜谱原始数据（eachDish 断言：tags / minutes / ingredients）。 */
    public Map<String, Object> recipe(String name) {
        return recipes.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElse(null);
    }

    // ================================================================= 内部

    @SuppressWarnings("unchecked")
    private List<CookLine> cookLines(Map<String, Object> r, int servings, List<ItemLine> overrides, boolean apply) {
        int base = r.get("servings") == null ? servings : Math.max(1, num(r.get("servings")).intValue());
        List<CookLine> out = new ArrayList<>();
        for (Map<String, Object> ing : list(r.get("ingredients"))) {
            String name = String.valueOf(ing.get("name"));
            String unit = ing.get("unit") == null ? "" : String.valueOf(ing.get("unit"));
            BigDecimal need = dec(ing.get("amount"));
            if (need != null) need = need.multiply(BigDecimal.valueOf(servings)).divide(BigDecimal.valueOf(base), 2, RoundingMode.HALF_UP);
            String forced = null;
            for (ItemLine o : overrides) {
                if (o.name().equals(name)) {
                    if (OVERRIDE_ALL.equals(o.amount()) || OVERRIDE_SKIP.equals(o.amount())) {
                        forced = o.amount();
                    } else {
                        need = dec(o.amount());
                        if (o.unit() != null) unit = o.unit();
                    }
                }
            }
            String delta = need == null ? "适量" : "-" + plain(plainNum(need)) + unit;
            if (OVERRIDE_SKIP.equals(forced)) {
                out.add(new CookLine(null, name, null, "不扣", "不变", "SKIP"));
                continue;
            }
            if (Boolean.TRUE.equals(ing.get("seasoning"))) {
                out.add(new CookLine(null, name, null, delta.replace("-", ""), "调料不计量", "SKIP"));
                continue;
            }
            Map<String, Object> p = pantryItem(name);
            if (p == null || "OUT".equals(p.get("status"))) {
                out.add(new CookLine(null, name, null, delta.replace("-", ""), "家里没有记录", "SKIP"));
                continue;
            }
            BigDecimal have = dec(p.get("amount"));
            boolean sameUnit = unit.equals(String.valueOf(p.get("unit")));
            String after;
            String status;
            if (OVERRIDE_ALL.equals(forced)) {
                after = "用完了";
                status = "USED_UP";
                if (apply) {
                    p.put("amount", 0);
                    p.put("status", "OUT");
                }
            } else if (have != null && need != null && sameUnit) {
                BigDecimal left = have.subtract(need);
                if (left.signum() <= 0) {
                    after = "用完了";
                    status = "USED_UP";
                    if (apply) {
                        p.put("amount", 0);
                        p.put("status", "OUT");
                    }
                } else {
                    after = "剩 " + plain(plainNum(left)) + unit;
                    status = "LEFT";
                    if (apply) p.put("amount", plainNum(left));
                }
            } else {
                boolean low = "LOW".equals(p.get("status"));
                after = low ? "用完了" : "还剩一些";
                status = low ? "USED_UP" : "LEFT";
                if (apply) p.put("status", low ? "OUT" : "LOW");
            }
            out.add(new CookLine(null, name, null, delta, after, status));
        }
        return out;
    }

    private DishInfo info(Map<String, Object> r) {
        int idx = recipes.indexOf(r);
        long id = idx >= 0 ? idx + 1 : FOREIGN_BASE + 1 + foreignRecipes.indexOf(r);
        List<IngredientLine> lines = list(r.get("ingredients")).stream()
                .map(i -> new IngredientLine(String.valueOf(i.get("name")),
                        i.get("amount") == null ? null : plain(i.get("amount")) + (i.get("unit") == null ? "" : i.get("unit")),
                        Boolean.TRUE.equals(i.get("seasoning")), null))
                .toList();
        return new DishInfo(id, String.valueOf(r.get("name")), null,
                r.get("minutes") == null ? null : num(r.get("minutes")).intValue(),
                r.get("difficulty") == null ? null : String.valueOf(r.get("difficulty")), tags(r), lines,
                r.get("sodiumMgPerServing") == null ? null : num(r.get("sodiumMgPerServing")).intValue(), coverage(r),
                strings(r.get("steps")), r.get("description") == null ? null : String.valueOf(r.get("description")));
    }

    private Coverage coverage(Map<String, Object> r) {
        int have = 0;
        int total = 0;
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> i : list(r.get("ingredients"))) {
            if (Boolean.TRUE.equals(i.get("seasoning"))) continue;
            total++;
            String n = String.valueOf(i.get("name"));
            Map<String, Object> p = pantryItem(n);
            if (p != null && !"OUT".equals(p.get("status"))) have++;
            else missing.add(n);
        }
        return new Coverage(have, total, missing);
    }

    private MenuInfo menu(Map<String, Object> m, long id) {
        return new MenuInfo(id, String.valueOf(m.get("name")),
                m.get("date") == null ? null : LocalDate.parse(String.valueOf(m.get("date"))),
                m.get("servings") == null ? 1 : num(m.get("servings")).intValue(),
                String.valueOf(m.getOrDefault("status", "ONGOING")), strings(m.get("dishes")), List.of(),
                m.get("note") == null ? null : String.valueOf(m.get("note")));
    }

    private Set<String> expiringNames() {
        Set<String> out = new LinkedHashSet<>();
        for (PantryItem p : pantry()) {
            if (p.daysLeft() != null && p.daysLeft() <= 3 && !"OUT".equals(p.status())) out.add(p.name());
        }
        return out;
    }

    private Map<String, Object> pantryItem(String name) {
        for (Map<String, Object> p : pantryList()) {
            if (sameFood(String.valueOf(p.get("name")), name)) return p;
            if (strings(p.get("aliases")).contains(name)) return p;
        }
        return null;
    }

    private static boolean sameFood(String a, String b) {
        return a.equals(b) || (a.length() >= 2 && b.length() >= 2 && (a.contains(b) || b.contains(a)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pantryList() {
        Object v = family.get("pantry");
        if (!(v instanceof List<?>)) {
            v = new ArrayList<>();
            family.put("pantry", v);
        }
        return (List<Map<String, Object>>) v;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> menuList() {
        Object v = family.get("menus");
        if (!(v instanceof List<?>)) {
            v = new ArrayList<>();
            family.put("menus", v);
        }
        return (List<Map<String, Object>>) v;
    }

    private String dishName(Long id) {
        return String.valueOf(recipes.get(id.intValue() - 1).get("name"));
    }

    private String memberName(Long id) {
        return members().stream().filter(m -> m.id() == id).map(MemberInfo::name).findFirst().orElse(String.valueOf(id));
    }

    private Map<String, Object> snapshot() {
        return Map.of("family", deepCopy(family));
    }

    private Map<String, Object> deepCopy(Map<String, Object> m) {
        return om.convertValue(m, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof Collection<?> c) for (Object o : c) if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        return out;
    }

    static List<String> strings(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof Collection<?> c) for (Object o : c) if (o != null) out.add(String.valueOf(o));
        return out;
    }

    private static List<String> tags(Map<String, Object> r) {
        return strings(r.get("tags"));
    }

    private static List<String> ingredientNames(Map<String, Object> r) {
        return list(r.get("ingredients")).stream().filter(i -> !Boolean.TRUE.equals(i.get("seasoning")))
                .map(i -> String.valueOf(i.get("name"))).toList();
    }

    private static Number num(Object v) {
        if (v instanceof Number n) return n;
        return new BigDecimal(String.valueOf(v).trim());
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        String s = String.valueOf(v).trim();
        return s.matches("-?\\d+(\\.\\d+)?") ? new BigDecimal(s) : null;
    }

    private static Object numOrText(String s) {
        BigDecimal d = dec(s);
        return d == null ? s : plainNum(d);
    }

    private static Object plainNum(BigDecimal d) {
        BigDecimal s = d.stripTrailingZeros();
        return s.scale() <= 0 ? (Object) s.longValueExact() : (Object) s.doubleValue();
    }

    private static String plain(Object v) {
        if (v instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        if (v instanceof Double d && d == Math.rint(d)) return String.valueOf(d.longValue());
        return String.valueOf(v);
    }

    private static String amountText(Map<String, Object> p) {
        if (p.get("amount") == null) return null;
        return plain(p.get("amount")) + (p.get("unit") == null ? "" : p.get("unit"));
    }

    private static LocalDateTime parseNow(String now, String today) {
        try {
            return OffsetDateTime.parse(now).toLocalDateTime();
        } catch (RuntimeException e) {
            try {
                return LocalDateTime.parse(now);
            } catch (RuntimeException e2) {
                return LocalDate.parse(Objects.requireNonNullElse(today, "2026-10-16")).atTime(17, 40);
            }
        }
    }

    static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = cur;
        }
        return prev[b.length()];
    }
}

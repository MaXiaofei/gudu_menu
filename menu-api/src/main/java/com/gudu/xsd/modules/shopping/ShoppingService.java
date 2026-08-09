package com.gudu.xsd.modules.shopping;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.mealplan.MealPlan;
import com.gudu.xsd.modules.mealplan.MealPlanItem;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanItemMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanMapper;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.IngredientStock;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.StockLog;
import com.gudu.xsd.modules.pantry.mapper.StockLogMapper;
import com.gudu.xsd.modules.notification.NotificationPayload;
import com.gudu.xsd.modules.notification.NotificationService;
import com.gudu.xsd.modules.shopping.ShoppingAggregator.Usage;
import com.gudu.xsd.modules.shopping.mapper.ShoppingItemMapper;
import com.gudu.xsd.modules.shopping.mapper.ShoppingListMapper;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采购清单服务（redesign）。
 *
 * <p>核心流程 generate(sourceType, sourceId/sourceIds)：
 * <ul>
 *   <li>menu  → 查 menu_dish 拿 dish_id 列表；</li>
 *   <li>dish  → 直接用传入的 dish_ids；</li>
 *   <li>plan  → 查 meal_plan_item 拿 dish_id 列表。</li>
 * </ul>
 * 然后查各 dish 的 dish_ingredient(ingredient_id + amount × servingFactor)
 * → join ingredient 拿 purchaseCategoryId → 组装 Usage →
 * aggregator.aggregate（按 ingredient_id 去重，合计 referenceGrams）
 * → 落 shopping_list + shopping_item 草稿（purchase_amount/unit 留 null）。
 *
 * <p>用户在前端填 purchase_amount + purchase_unit_id → updatePurchase。
 *
 * <p>不做估价。VO 带中文（食材名/采购单位名 斤把个，枚举铁律）。
 *
 * <p>参照 MealPlanService / PantryService 范式：ServiceImpl 主表 + 显式多 Mapper 构造。
 */
@Service
public class ShoppingService extends ServiceImpl<ShoppingListMapper, ShoppingList> {

    private final ShoppingItemMapper itemMapper;
    private final MealPlanItemMapper mealPlanItemMapper;
    private final MealPlanMapper mealPlanMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final IngredientMapper ingredientMapper;
    private final DictMapper dictMapper;
    private final MenuDishMapper menuDishMapper;
    private final ShoppingAggregator aggregator;
    private final NotificationService notificationService;
    private final PantryService pantryService;
    private final StockLogMapper stockLogMapper;

    @Autowired
    public ShoppingService(ShoppingItemMapper itemMapper,
                           MealPlanItemMapper mealPlanItemMapper,
                           MealPlanMapper mealPlanMapper,
                           DishIngredientMapper dishIngredientMapper,
                           IngredientMapper ingredientMapper,
                           DictMapper dictMapper,
                           MenuDishMapper menuDishMapper,
                           ShoppingAggregator aggregator,
                           NotificationService notificationService,
                           PantryService pantryService,
                           StockLogMapper stockLogMapper) {
        this.itemMapper = itemMapper;
        this.mealPlanItemMapper = mealPlanItemMapper;
        this.mealPlanMapper = mealPlanMapper;
        this.dishIngredientMapper = dishIngredientMapper;
        this.ingredientMapper = ingredientMapper;
        this.dictMapper = dictMapper;
        this.menuDishMapper = menuDishMapper;
        this.aggregator = aggregator;
        this.notificationService = notificationService;
        this.pantryService = pantryService;
        this.stockLogMapper = stockLogMapper;
    }

    // ===================== 生成（三数据源） =====================

    /**
     * 通用生成：根据数据源类型解析涉及的 dish_id 列表，聚合食材用量，落库采购草稿。
     *
     * @param sourceType 数据源：menu / dish / plan
     * @param sourceId   menu 或 plan 的 id（dish 时可空，用 sourceIds）
     * @param sourceIds  dish 数据源时的多选 dish_id 列表
     * @return 新生成的 shopping_list.id
     */
    @Transactional
    public Long generate(String sourceType, Long sourceId, List<Long> sourceIds) {
        List<DishUsage> dishUsages = resolveDishes(sourceType, sourceId, sourceIds);

        // 收集 dish_id → 各菜用量（dish_ingredient）
        List<Long> dishIds = dishUsages.stream().map(DishUsage::dishId).distinct().collect(Collectors.toList());
        List<DishIngredient> dis = dishIds.isEmpty()
                ? List.of()
                : dishIngredientMapper.selectList(new QueryWrapper<DishIngredient>().in("dish_id", dishIds));

        // dish_id → servingFactor
        Map<Long, BigDecimal> factorByDish = new HashMap<>();
        for (DishUsage du : dishUsages) {
            factorByDish.merge(du.dishId, du.servingFactor, BigDecimal::add);
        }

        // ingredient_id → ingredient（拿 purchase_category）
        List<Long> ingIds = dis.stream().map(DishIngredient::getIngredientId).distinct().collect(Collectors.toList());
        Map<Long, Ingredient> ingById = ingIds.isEmpty()
                ? Map.of()
                : ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ingIds)).stream()
                        .collect(Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));

        // 组装 Usage（克数 × 份数系数）
        List<Usage> usages = new ArrayList<>();
        for (DishIngredient di : dis) {
            Ingredient ing = ingById.get(di.getIngredientId());
            if (ing == null) continue;
            BigDecimal factor = factorByDish.getOrDefault(di.getDishId(), BigDecimal.ONE);
            // 改读 grams（内部克基准）；旧数据迁移后 grams=amount，兼容
            BigDecimal baseGrams = di.getGrams() != null ? di.getGrams()
                    : (di.getAmount() != null ? di.getAmount() : BigDecimal.ZERO);
            usages.add(new Usage(ing.getId(), baseGrams.multiply(factor), ing.getPurchaseCategoryId()));
        }

        // 聚合（按 ingredient_id 去重 → referenceGrams）
        List<ShoppingAggregator.ShoppingLine> lines = aggregator.aggregate(usages);

        // 落库
        ShoppingList list = newList(sourceType, sourceId);
        save(list);
        for (ShoppingAggregator.ShoppingLine l : lines) {
            ShoppingItem item = new ShoppingItem();
            item.setListId(list.getId());
            item.setIngredientId(l.ingredientId());
            item.setReferenceGrams(l.referenceGrams());
            item.setPurchaseCategoryId(l.purchaseCategoryId());
            // 兼容旧字段：referenceGrams 同步写 totalAmount（参考用，不删列）
            item.setTotalAmount(l.referenceGrams());
            item.setPurchased(0);
            // purchase_amount / purchase_unit_id 留 null（用户后填）
            itemMapper.insert(item);
        }

        notifyShoppingGenerated(sourceType, lines.size());
        return list.getId();
    }

    /** 一笔 dish 用量：dish_id + 份数系数（plan 取排菜项份数；menu/dish 默认 1）。 */
    private record DishUsage(Long dishId, BigDecimal servingFactor) {}

    /** 根据数据源类型解析涉及的 dish_id 列表（含份数系数）。 */
    private List<DishUsage> resolveDishes(String sourceType, Long sourceId, List<Long> sourceIds) {
        if (sourceType == null) sourceType = "plan";
        switch (sourceType) {
            case "menu": {
                if (sourceId == null) return List.of();
                List<MenuDish> mds = menuDishMapper.selectList(
                        new QueryWrapper<MenuDish>().eq("menu_id", sourceId));
                List<DishUsage> out = new ArrayList<>();
                for (MenuDish md : mds) {
                    BigDecimal f = md.getServingFactor() == null ? BigDecimal.ONE : md.getServingFactor();
                    out.add(new DishUsage(md.getDishId(), f));
                }
                return out;
            }
            case "dish": {
                List<Long> ids = sourceIds == null ? List.of() : sourceIds;
                List<DishUsage> out = new ArrayList<>();
                for (Long did : ids) {
                    if (did != null) out.add(new DishUsage(did, BigDecimal.ONE));
                }
                return out;
            }
            case "plan":
            default: {
                if (sourceId == null) return List.of();
                List<MealPlanItem> planItems = mealPlanItemMapper.selectList(
                        new QueryWrapper<MealPlanItem>().eq("plan_id", sourceId));
                List<DishUsage> out = new ArrayList<>();
                for (MealPlanItem pi : planItems) {
                    BigDecimal f = pi.getServingFactor() == null ? BigDecimal.ONE : pi.getServingFactor();
                    out.add(new DishUsage(pi.getDishId(), f));
                }
                return out;
            }
        }
    }

    /** 给当前 session 的就餐成员发「采购清单已生成」站内通知；无 session 则跳过。 */
    private void notifyShoppingGenerated(String sourceType, int itemCount) {
        Long memberId;
        try {
            memberId = StpUtil.getSession().getLong("currentMemberId");
        } catch (Exception e) {
            return;
        }
        if (memberId == null) return;
        String src = sourceType == null ? "周计划" :
                ("menu".equals(sourceType) ? "菜单" : "dish".equals(sourceType) ? "菜品" : "周计划");
        notificationService.send(
                new NotificationPayload(memberId, "shopping",
                        "采购清单已生成",
                        src + "已生成采购清单，共 " + itemCount + " 项"),
                "in_app");
    }

    private ShoppingList newList(String sourceType, Long sourceId) {
        ShoppingList list = new ShoppingList();
        list.setSourcePlanId("plan".equals(sourceType) ? sourceId : null);
        list.setSourceMenuId("menu".equals(sourceType) ? sourceId : null);  // Plan E: 食集溯源
        list.setTimeRange(sourceType);
        // menu/dish 来源没有固定周区间，用当天；plan 来源取该计划的周区间
        if ("plan".equals(sourceType) && sourceId != null) {
            MealPlan plan = mealPlanMapper.selectById(sourceId);
            if (plan != null && plan.getWeekStart() != null) {
                list.setStartDate(plan.getWeekStart());
                list.setEndDate(plan.getWeekStart().plusDays(6));
                return list;
            }
        }
        list.setStartDate(LocalDate.now());
        list.setEndDate(LocalDate.now());
        return list;
    }

    /**
     * 建空采购单（自定义采购入口）：仅落一条 shopping_list（time_range=custom），
     * 不预置采购项。MP 的 save 会自动回填 id。
     *
     * @return 新生成的 shopping_list.id
     */
    @Transactional
    public Long createEmpty() {
        ShoppingList sl = new ShoppingList();
        sl.setTimeRange("custom");
        sl.setStartDate(LocalDate.now());
        sl.setEndDate(LocalDate.now());
        save(sl);
        return sl.getId();
    }

    // ===================== 查询 =====================

    /** 采购清单详情：含 items（带中文）+ 按品类分区视图。 */
    public ShoppingListVO getDetail(Long listId) {
        ShoppingList list = getById(listId);
        List<ShoppingItem> rows = itemMapper.selectList(
                new QueryWrapper<ShoppingItem>().eq("list_id", listId)
                        .orderByAsc("purchase_category_id").orderByAsc("ingredient_id"));
        List<ShoppingItemVO> items = fillVoNames(rows);
        ShoppingListVO vo = new ShoppingListVO();
        BeanUtils.copyProperties(list, vo);
        vo.setItems(items);
        // 分区视图
        Map<Long, List<ShoppingItemVO>> grouped = new LinkedHashMap<>();
        Map<Long, String> catNames = new LinkedHashMap<>();
        for (ShoppingItemVO it : items) {
            grouped.computeIfAbsent(it.getPurchaseCategoryId(), k -> new ArrayList<>()).add(it);
            if (it.getPurchaseCategoryId() != null) {
                catNames.putIfAbsent(it.getPurchaseCategoryId(), it.getPurchaseCategoryName());
            }
        }
        vo.setGrouped(grouped);
        vo.setCategoryNames(catNames);
        return vo;
    }

    /**
     * 按食集查最新采购清单（含 items + 三色 + 分区）。Plan E。
     * 未生成（source_menu_id 无命中）返回 null，前端据此显「生成采购清单」按钮。
     */
    public ShoppingListVO getByMenu(Long menuId) {
        if (menuId == null) return null;
        List<ShoppingList> rows = list(new QueryWrapper<ShoppingList>()
                .eq("source_menu_id", menuId)
                .orderByDesc("created_at").last("LIMIT 1"));
        if (rows.isEmpty()) return null;
        return getDetail(rows.get(0).getId());
    }

    /**
     * 备菜一键加采购（V42）：把备菜清单里勾选的食材加入该食集的采购清单。
     * 该食集已有清单（source_menu_id）则复用，无则新建；逐项追加，同清单同食材去重；
     * reference_grams 取食集聚合用量（备菜 Tab 展示用）。
     *
     * @return 采购清单 id
     */
    @Transactional
    public Long fromPrep(Long menuId, List<Long> ingredientIds) {
        if (menuId == null) throw new BizException("食集 id 不能为空");
        List<Long> valid = ingredientIds == null ? List.of()
                : ingredientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) throw new BizException("请选择要加入采购的食材");

        List<ShoppingList> rows = list(new QueryWrapper<ShoppingList>()
                .eq("source_menu_id", menuId).orderByDesc("created_at").last("LIMIT 1"));
        Long listId;
        if (rows.isEmpty()) {
            ShoppingList sl = new ShoppingList();
            sl.setSourceMenuId(menuId);
            sl.setTimeRange("menu_prep");
            sl.setStartDate(LocalDate.now());
            sl.setEndDate(LocalDate.now());
            save(sl);
            listId = sl.getId();
        } else {
            listId = rows.get(0).getId();
        }

        Map<Long, BigDecimal> needByIng = aggregateMenuNeedGrams(menuId);
        int added = 0;
        for (Long ingId : valid) {
            Long count = itemMapper.selectCount(new QueryWrapper<ShoppingItem>()
                    .eq("list_id", listId).eq("ingredient_id", ingId));
            if (count != null && count > 0) continue; // 已在清单中，去重
            ShoppingItem item = new ShoppingItem();
            item.setListId(listId);
            item.setIngredientId(ingId);
            item.setReferenceGrams(needByIng.get(ingId));
            item.setTotalAmount(BigDecimal.ZERO); // total_amount NOT NULL 无默认值，兜底 0
            item.setPurchased(0);
            itemMapper.insert(item);
            added++;
        }
        if (added == 0) throw new BizException("所选食材已在采购清单中");
        return listId;
    }

    /** 食集聚合用量（by ingredientId，fromPrep 填 reference_grams 用）。 */
    private Map<Long, BigDecimal> aggregateMenuNeedGrams(Long menuId) {
        List<MenuDish> mds = menuDishMapper.selectList(new QueryWrapper<MenuDish>().eq("menu_id", menuId));
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct().toList();
        if (dishIds.isEmpty()) return Map.of();
        List<DishIngredient> rows = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().in("dish_id", dishIds));
        Map<Long, BigDecimal> factorByDish = new HashMap<>();
        for (MenuDish md : mds) {
            if (md.getDishId() == null) continue;
            BigDecimal f = md.getServingFactor() == null ? BigDecimal.ONE : md.getServingFactor();
            factorByDish.merge(md.getDishId(), f, BigDecimal::add);
        }
        Map<Long, BigDecimal> need = new HashMap<>();
        for (DishIngredient di : rows) {
            if (di.getIngredientId() == null || di.getGrams() == null) continue;
            BigDecimal f = factorByDish.getOrDefault(di.getDishId(), BigDecimal.ONE);
            need.merge(di.getIngredientId(), di.getGrams().multiply(f), BigDecimal::add);
        }
        return need;
    }

    /**
     * 批量保存入库（B2）：本次勾选的采购项一次性入库（默认记「充足」），customName 手动项只标已买。
     * 不逐项弹档位选择（DESIGN.md §15 批量提交优先）；写 purchase 流水（带 ref_id 溯源，供撤回）。
     *
     * @return restocked=成功入库数（关联食材的项）；markedOnly=只标已买数（手动项/无食材项）
     */
    @Transactional
    public RestockResult restock(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BizException("请选择要入库的采购项");
        }
        int restocked = 0;
        int markedOnly = 0;
        for (Long id : itemIds) {
            if (id == null) continue;
            ShoppingItem it = itemMapper.selectById(id);
            if (it == null) continue;
            if (it.getIngredientId() != null) {
                pantryService.setLevel(it.getIngredientId(), IngredientStock.LEVEL_ENOUGH,
                        StockLog.ACTION_PURCHASE, null, id);
                restocked++;
            } else {
                markedOnly++;
            }
            if (it.getPurchased() == null || it.getPurchased() == 0) {
                it.setPurchased(1);
                itemMapper.updateById(it);
            }
        }
        if (restocked == 0 && markedOnly == 0) {
            throw new BizException("未找到有效的采购项");
        }
        return new RestockResult(restocked, markedOnly);
    }

    /** 批量入库结果。 */
    public record RestockResult(int restocked, int markedOnly) {
    }

    /**
     * 撤回入库（B3）：按 stock_log.ref_id 溯源该采购项最近一次入库流水，恢复 before_level
     * （入库时新建档 before=null → 删除档位回到没建档），然后删除该采购项。
     */
    @Transactional
    public void undoRestock(Long itemId) {
        if (itemId == null) throw new BizException("采购项 id 不能为空");
        ShoppingItem it = itemMapper.selectById(itemId);
        if (it == null) throw new BizException("采购项不存在");
        if (it.getIngredientId() == null) {
            throw new BizException("手动项没有入库记录，可直接移除");
        }
        List<StockLog> logs = stockLogMapper.selectList(new QueryWrapper<StockLog>()
                .eq("ref_id", itemId).orderByDesc("create_time").last("LIMIT 1"));
        if (logs.isEmpty()) {
            throw new BizException("没有可撤回的入库记录");
        }
        String before = logs.get(0).getBeforeLevel();
        if (before == null) {
            pantryService.removeLevel(it.getIngredientId(), StockLog.ACTION_UNDO, null, itemId);
        } else {
            pantryService.setLevel(it.getIngredientId(), before, StockLog.ACTION_UNDO, null, itemId);
        }
        itemMapper.deleteById(itemId);
    }

    /**
     * 清单改名（B4，自定义采购 ✎）：name trim 后非空。
     */
    @Transactional
    public void renameList(Long listId, String name) {
        if (listId == null) throw new BizException("采购清单 id 不能为空");
        if (name == null || name.isBlank()) throw new BizException("清单名不能为空");
        ShoppingList list = getById(listId);
        if (list == null) throw new BizException("采购清单不存在");
        list.setName(name.trim());
        updateById(list);
    }

    /** 分页查采购清单（后台管理，不含 items，按创建时间倒序）。 */
    public IPage<ShoppingList> page(PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<ShoppingList>().orderByDesc("created_at"));
    }

    /** 用户填采购量 + 采购单位（PUT /shopping/item/{id}）。 */
    public void updatePurchase(Long itemId, BigDecimal purchaseAmount, Long purchaseUnitId) {
        ShoppingItem it = itemMapper.selectById(itemId);
        if (it == null) return;
        it.setPurchaseAmount(purchaseAmount);
        it.setPurchaseUnitId(purchaseUnitId);
        itemMapper.updateById(it);
    }

    /**
     * 手动添加自定义采购项（V30）：采购清单不强绑菜单/菜品。
     * <p>规则：name 命中 ingredient 表（精确名匹配）→ 关联 ingredientId + 顺带带出该食材的 purchaseCategoryId；
     * 未命中 → ingredientId 留空、name 存 custom_name，purchaseCategoryId 用前端传值（可空）。
     *
     * @param listId             目标采购清单 id
     * @param name               用户输入的食材名（必填，trim 后非空）
     * @param amount             采购量（可空，用户后填）
     * @param unitId             采购单位 sys_dict(group=purchase_unit) id（可空）
     * @param purchaseCategoryId 采购品类 sys_dict(group=purchase_category) id（可空，命中 ingredient 时被食材自身品类覆盖）
     * @return 新增的 shopping_item.id
     */
    @Transactional
    public Long addItemCustom(Long listId, String name, BigDecimal amount, Long unitId, Long purchaseCategoryId) {
        if (listId == null) throw new IllegalArgumentException("listId 不能为空");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("食材名不能为空");
        String trimmed = name.trim();

        ShoppingItem item = new ShoppingItem();
        item.setListId(listId);
        item.setPurchaseAmount(amount);
        item.setPurchaseUnitId(unitId);
        item.setPurchased(0);

        // name 命中已有 ingredient → 关联，并带出其 purchaseCategoryId（前端传值作兜底）
        List<Ingredient> matched = ingredientMapper.selectList(
                new QueryWrapper<Ingredient>().eq("name", trimmed).last("LIMIT 1"));
        if (!matched.isEmpty()) {
            Ingredient ing = matched.get(0);
            item.setIngredientId(ing.getId());
            item.setPurchaseCategoryId(ing.getPurchaseCategoryId() != null
                    ? ing.getPurchaseCategoryId() : purchaseCategoryId);
            item.setCustomName(null);
        } else {
            // 未命中：纯自定义项，ingredientId 留空，name 存 custom_name
            item.setIngredientId(null);
            item.setCustomName(trimmed);
            item.setPurchaseCategoryId(purchaseCategoryId);
        }
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * 勾选/取消勾选某明细已买。
     *
     * <p>入库（V42）：仅 0→1（勾选已买）且关联食材时，把该食材档位设为 level（默认 ENOUGH 充足），
     * 记一笔 purchase 流水；1→0（取消）不反向扣减（库存可能已消耗，避免误扣）。
     * customName 手动加项（ingredientId 为空）只标 purchased，不入库存。
     *
     * @param level 入库档位（可空，默认 ENOUGH；买得少可选 LOW）
     */
    @Transactional
    public void togglePurchased(Long itemId, String level) {
        ShoppingItem it = itemMapper.selectById(itemId);
        if (it == null) return;
        int cur = it.getPurchased() == null ? 0 : it.getPurchased();
        int next = cur == 1 ? 0 : 1;
        it.setPurchased(next);
        itemMapper.updateById(it);
        if (cur == 0 && it.getIngredientId() != null) {
            pantryService.setLevel(it.getIngredientId(),
                    (level == null || level.isBlank()) ? IngredientStock.LEVEL_ENOUGH : level,
                    StockLog.ACTION_PURCHASE, null, itemId);
        }
    }

    /** 删除某明细。 */
    public void deleteItem(Long itemId) {
        itemMapper.deleteById(itemId);
    }

    /** 删除整张清单。 */
    public void deleteList(Long listId) {
        itemMapper.delete(new QueryWrapper<ShoppingItem>().eq("list_id", listId));
        removeById(listId);
    }

    /** 给 item 列表填中文展示名（食材名/单位名/品类名/采购单位名）+ 档位 badge（V42）。 */
    private List<ShoppingItemVO> fillVoNames(List<ShoppingItem> rows) {
        if (rows.isEmpty()) return new ArrayList<>();
        // 食材名
        List<Long> ingIds = rows.stream().map(ShoppingItem::getIngredientId).distinct().collect(Collectors.toList());
        Map<Long, String> ingName = ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ingIds))
                .stream().collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
        // 档位 badge（V42）：读 ingredient_stock，映射 没有/快用完/有；无建档的项标灰（不标记）
        Map<Long, String> levelByIng = pantryService.levelMap(ingIds);
        // 单位 + 品类 + 采购单位字典（一次性查三组）
        List<SysDict> dicts = dictMapper.selectList(
                new QueryWrapper<SysDict>().in("dict_group", List.of("unit", "purchase_category", "purchase_unit")));
        Map<Long, String> unitName = new HashMap<>();
        Map<Long, String> catName = new HashMap<>();
        Map<Long, String> purchaseUnitName = new HashMap<>();
        for (SysDict d : dicts) {
            switch (d.getDictGroup()) {
                case "unit" -> unitName.put(d.getId(), d.getName());
                case "purchase_category" -> catName.put(d.getId(), d.getName());
                case "purchase_unit" -> purchaseUnitName.put(d.getId(), d.getName());
            }
        }
        List<ShoppingItemVO> out = new ArrayList<>(rows.size());
        for (ShoppingItem it : rows) {
            ShoppingItemVO vo = new ShoppingItemVO();
            BeanUtils.copyProperties(it, vo);
            // 展示名：有 ingredientId 取食材名；否则用 custom_name（V30 手动添加项）
            String display = it.getIngredientId() != null ? ingName.get(it.getIngredientId()) : null;
            if (display == null || display.isEmpty()) {
                display = it.getCustomName();
            }
            vo.setIngredientName(display);
            vo.setUnitName(unitName.get(it.getUnitId()));
            vo.setPurchaseCategoryName(catName.get(it.getPurchaseCategoryId()));
            vo.setPurchaseUnitName(purchaseUnitName.get(it.getPurchaseUnitId()));
            // 兜底标灰：有 ingredientId 且 referenceGrams 为 null/0 视为未配置换算
            vo.setConvertConfigured(it.getIngredientId() == null
                    || (it.getReferenceGrams() != null && it.getReferenceGrams().compareTo(BigDecimal.ZERO) > 0));
            // 档位 badge：ingredientId 为空（手动加项）或没建档 → null（前端标灰「手动加」）
            String level = it.getIngredientId() == null ? null : levelByIng.get(it.getIngredientId());
            vo.setStockStatus(mapLevelStatus(level));
            vo.setPantryGrams(null);
            vo.setShortageGrams(null);
            out.add(vo);
        }
        return out;
    }

    /** 档位 → 三色 badge 映射（V42）：ENOUGH→够，LOW→快用完（旧 YELLOW_SHORT 语义），NONE→没有；无档位→null。 */
    private String mapLevelStatus(String level) {
        if (level == null) return null;
        return switch (level) {
            case IngredientStock.LEVEL_ENOUGH -> "GREEN_ENOUGH";
            case IngredientStock.LEVEL_LOW -> "YELLOW_SHORT";
            case IngredientStock.LEVEL_NONE -> "RED_NONE";
            default -> null;
        };
    }

    // ===================== 自定义文本生成 =====================

    /**
     * 从自由文本生成采购清单（纯 Java 解析，不依赖 AI）。
     *
     * <p>流程：文本解析 → 匹配食材库 → 按品类分区 → 落库。
     * 文本中未能匹配食材库的项 → 存为 customName。
     */
    @Transactional
    public Long generateFromText(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException("请输入采购内容");
        }

        // 1. 解析文本
        ShoppingTextParser parser = new ShoppingTextParser();
        List<ShoppingTextParser.ParsedItem> parsed = parser.parse(text);
        if (parsed.isEmpty()) {
            throw new BizException("未识别到有效采购项，请按「名称 数量单位」格式输入");
        }

        // 2. 加载食材库（用于名称匹配），兜底空列表
        List<Ingredient> all = ingredientMapper.selectList(null);
        if (all == null) all = java.util.Collections.emptyList();
        final List<Ingredient> finalAll = all;
        List<ShoppingTextParser.IngredientRef> pool = all.stream()
                .filter(i -> i != null && i.getName() != null)
                .map(i -> new ShoppingTextParser.IngredientRef(i.getId(), i.getName(), i.getPurchaseCategoryId()))
                .toList();

        // 3. 创建采购单
        ShoppingList list = new ShoppingList();
        list.setTimeRange("custom_text");
        list.setStartDate(LocalDate.now());
        list.setEndDate(LocalDate.now());
        save(list);

        // 4. 逐项落库
        for (ShoppingTextParser.ParsedItem pi : parsed) {
            ShoppingItem item = new ShoppingItem();
            item.setListId(list.getId());
            item.setPurchased(0);
            item.setReferenceGrams(pi.gramsEstimate());
            // total_amount 列 NOT NULL 无默认值，兜底设 0
            item.setTotalAmount(BigDecimal.ZERO);

            Long matchedId = ShoppingTextParser.matchIngredient(pi.name(), pool);
            if (matchedId != null) {
                item.setIngredientId(matchedId);
                // 带出食材的采购品类
                for (ShoppingTextParser.IngredientRef ref : pool) {
                    if (ref.id().equals(matchedId)) {
                        item.setPurchaseCategoryId(ref.purchaseCategoryId());
                        break;
                    }
                }
                item.setCustomName(null);
            } else {
                item.setIngredientId(null);
                item.setCustomName(pi.name());
            }
            itemMapper.insert(item);
        }

        return list.getId();
    }
}

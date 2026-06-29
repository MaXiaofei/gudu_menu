package com.gudu.xsd.modules.mealplan;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanItemMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanMapper;
import com.gudu.xsd.modules.mealplan.mapper.MenuTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 周计划服务。
 *
 * 纯函数 detectDuplicates 是算法地基（不依赖外部状态，可单测，参照 MenuCalcService）。
 * CRUD + applyTemplate 依赖三个 Mapper。
 *
 * 注：测试 new MealPlanService(null,null,null)，故显式三参构造（@Autowired 主构造）。
 * ServiceImpl 的 baseMapper 由 MyBatis-Plus 自身的注入机制填充，无需在本构造里赋值。
 */
@Service
public class MealPlanService extends ServiceImpl<MealPlanMapper, MealPlan> {

    private final MealPlanItemMapper itemMapper;
    private final MenuTemplateMapper templateMapper;
    private final DishMapper dishMapper;

    @Autowired
    public MealPlanService(MealPlanMapper planMapper,
                           MealPlanItemMapper itemMapper,
                           MenuTemplateMapper templateMapper,
                           DishMapper dishMapper) {
        // planMapper 由 ServiceImpl 通过自身注入机制持有（baseMapper），此处仅为对齐测试的三参构造签名。
        this.itemMapper = itemMapper;
        this.templateMapper = templateMapper;
        this.dishMapper = dishMapper;
    }

    // ===================== 纯函数（算法地基） =====================

    /** 排菜项的三元组键：菜 + 日期 + 餐次。 */
    public record Item(Long dishId, LocalDate date, String meal) {}

    /**
     * 检测同日同餐重复菜：按 (dishId, date, meal) 分组，count>1 的返回。
     * 纯函数，不读库。供 Controller 在 saveItem 时返回提示。
     *
     * @return 重复出现的 Item 列表（保留重复项，去重一次）
     */
    public List<Item> detectDuplicates(List<Item> items) {
        if (items == null || items.isEmpty()) return List.of();
        Map<Item, Integer> cnt = new HashMap<>();
        for (Item it : items) {
            cnt.merge(it, 1, Integer::sum);
        }
        List<Item> dup = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : cnt.entrySet()) {
            if (e.getValue() > 1) dup.add(e.getKey());
        }
        return dup;
    }

    // ===================== CRUD =====================

    /** 分页查周计划（后台管理）。按创建时间倒序。 */
    public IPage<MealPlan> page(PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<MealPlan>().orderByDesc("create_time"));
    }

    /** 创建周计划。 */
    public Long createPlan(LocalDate weekStart, String name) {
        MealPlan p = new MealPlan();
        p.setWeekStart(weekStart);
        p.setName(name);
        save(p);
        return p.getId();
    }

    /** 周计划详情：含 items（每项带 dishName）。 */
    public PlanDetail getPlan(Long planId) {
        MealPlan plan = getById(planId);
        List<MealPlanItem> items = itemMapper.selectList(
                new QueryWrapper<MealPlanItem>().eq("plan_id", planId)
                        .orderByAsc("date").orderByAsc("sort").orderByAsc("id"));
        fillDishNames(items);
        return new PlanDetail(plan, items);
    }

    /** 批量填充 dishName：查 dish 表，按 id 映射。 */
    private void fillDishNames(List<MealPlanItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> dishIds = items.stream()
                .map(MealPlanItem::getDishId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (dishIds.isEmpty()) return;
        List<com.gudu.xsd.modules.dish.Dish> dishes = dishMapper.selectBatchIds(dishIds);
        Map<Long, String> nameMap = new HashMap<>();
        for (com.gudu.xsd.modules.dish.Dish d : dishes) {
            nameMap.put(d.getId(), d.getName());
        }
        for (MealPlanItem item : items) {
            if (item.getDishId() != null) {
                item.setDishName(nameMap.getOrDefault(item.getDishId(), "#" + item.getDishId()));
            }
        }
    }

    /** 某周计划下所有排菜项。 */
    public List<MealPlanItem> listWeek(Long planId) {
        return itemMapper.selectList(
                new QueryWrapper<MealPlanItem>().eq("plan_id", planId)
                        .orderByAsc("date").orderByAsc("sort").orderByAsc("id"));
    }

    /**
     * 保存（新增/更新）一个排菜项。
     * 返回当前 plan 下与该 item 同日同餐的重复项（含自身），供前端提示。
     */
    public List<Item> saveItem(MealPlanItem item) {
        if (item.getServingFactor() == null) {
            item.setServingFactor(java.math.BigDecimal.ONE);
        }
        if (item.getSort() == null) item.setSort(0);
        if (item.getId() == null) {
            try {
                itemMapper.insert(item);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 同日同餐同菜已存在（DB UNIQUE 约束），构造重复提示
                List<Item> dup = new java.util.ArrayList<>();
                dup.add(new Item(item.getDishId(), item.getDate(), item.getMeal()));
                return dup;
            }
        } else {
            itemMapper.updateById(item);
        }
        // 检测当前 plan 下重复（同 plan/date/meal/dish 已有 UNIQUE 约束兜底，这里返回提示）
        return detectDuplicates(itemsOfPlanAsItem(item.getPlanId()));
    }

    public void deleteItem(Long itemId) {
        itemMapper.deleteById(itemId);
    }

    // ===================== 模板 =====================

    /** 套用模板：读 snapshot → 批量插入到目标 plan。 */
    @Transactional
    public int applyTemplate(Long templateId, Long planId) {
        MenuTemplate t = templateMapper.selectById(templateId);
        if (t == null || t.getSnapshot() == null || t.getSnapshot().isEmpty()) {
            return 0;
        }
        int n = 0;
        for (MealPlanItem src : t.getSnapshot()) {
            MealPlanItem it = new MealPlanItem();
            it.setPlanId(planId);
            it.setDate(src.getDate());
            it.setMeal(src.getMeal());
            it.setDishId(src.getDishId());
            it.setServingFactor(src.getServingFactor() != null ? src.getServingFactor() : java.math.BigDecimal.ONE);
            it.setSort(src.getSort() != null ? src.getSort() : 0);
            // UNIQUE(plan,date,meal,dish) 冲突则跳过（IGNORE 不易表达，catch 异常跳过单条）
            try {
                itemMapper.insert(it);
                n++;
            } catch (org.springframework.dao.DuplicateKeyException ignore) {
                // 同日同餐同菜已存在，跳过
            }
        }
        return n;
    }

    public List<MenuTemplate> listTemplates() {
        return templateMapper.selectList(new QueryWrapper<MenuTemplate>().orderByDesc("id"));
    }

    /**
     * 复制上周排菜到本周：把 srcPlanId 的所有 items 复制到 planId。
     * 日期偏移 7 天（上周一 → 本周一）。重复项跳过。
     */
    @Transactional
    public int copyPlanItems(Long srcPlanId, Long planId) {
        List<MealPlanItem> srcItems = itemMapper.selectList(
                new QueryWrapper<MealPlanItem>().eq("plan_id", srcPlanId));
        if (srcItems.isEmpty()) return 0;

        MealPlan srcPlan = getById(srcPlanId);
        MealPlan dstPlan = getById(planId);
        // 计算日期偏移：目标周的周一 - 源周的周一
        long dayOffset = 0;
        if (srcPlan != null && dstPlan != null
                && srcPlan.getWeekStart() != null && dstPlan.getWeekStart() != null) {
            dayOffset = java.time.temporal.ChronoUnit.DAYS.between(
                    srcPlan.getWeekStart(), dstPlan.getWeekStart());
        }

        int n = 0;
        for (MealPlanItem src : srcItems) {
            MealPlanItem it = new MealPlanItem();
            it.setPlanId(planId);
            // 日期偏移
            if (src.getDate() != null && dayOffset != 0) {
                it.setDate(src.getDate().plusDays(dayOffset));
            } else {
                it.setDate(src.getDate());
            }
            it.setMeal(src.getMeal());
            it.setDishId(src.getDishId());
            it.setServingFactor(src.getServingFactor() != null ? src.getServingFactor() : java.math.BigDecimal.ONE);
            it.setSort(src.getSort() != null ? src.getSort() : 0);
            try {
                itemMapper.insert(it);
                n++;
            } catch (org.springframework.dao.DuplicateKeyException ignore) {
                // 同日同餐同菜已存在，跳过
            }
        }
        return n;
    }

    public Long saveTemplate(MenuTemplate t) {
        if (t.getId() == null) {
            templateMapper.insert(t);
        } else {
            templateMapper.updateById(t);
        }
        return t.getId();
    }

    // ===================== 内部辅助 =====================

    private List<Item> itemsOfPlanAsItem(Long planId) {
        List<MealPlanItem> rows = itemMapper.selectList(
                new QueryWrapper<MealPlanItem>().eq("plan_id", planId));
        List<Item> list = new ArrayList<>(rows.size());
        for (MealPlanItem r : rows) {
            list.add(new Item(r.getDishId(), r.getDate(), r.getMeal()));
        }
        return list;
    }

    /** 周计划详情：计划 + items。 */
    public record PlanDetail(MealPlan plan, List<MealPlanItem> items) {}
}

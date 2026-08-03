package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.mapper.PantryMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 食材库存服务。
 *
 * 纯函数 isExpiring / isLow 是算法地基（不依赖外部状态，可单测，参照 MealPlanService.detectDuplicates）。
 * CRUD + listExpiring + listLow 依赖 PantryMapper；列表 VO 的食材名/单位名按需 join。
 *
 * 注：测试 new PantryService(null)，故显式单参构造（@Autowired 主构造）。
 * ServiceImpl 的 baseMapper 由 MyBatis-Plus 自身注入机制填充，无需在本构造里赋值。
 */
@Service
public class PantryService extends ServiceImpl<PantryMapper, Pantry> {

    private final IngredientMapper ingredientMapper;
    private DictMapper dictMapper;
    private com.gudu.xsd.modules.nutrition.UnitConvertService unitConvert;

    /** FIFO 扣减规划（无状态纯函数，内联持有）。 */
    private final PantryDeductionPlanner deductionPlanner = new PantryDeductionPlanner();

    @Autowired
    public PantryService(IngredientMapper ingredientMapper) {
        // 测试 new PantryService(null)：传 ingredientMapper，dictMapper 运行期由 ApplicationContext 注入。
        // 为兼顾测试（单参构造）与运行期（需要 DictMapper），dictMapper 走字段注入（@Autowired 字段亦可）。
        this.ingredientMapper = ingredientMapper;
        this.dictMapper = null; // 运行期由 setter 注入（见下），测试中保持 null 即可（纯函数不触达）
    }

    @Autowired
    public void setDictMapper(DictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Autowired
    public void setUnitConvert(com.gudu.xsd.modules.nutrition.UnitConvertService unitConvert) {
        this.unitConvert = unitConvert;
    }

    // ===================== 纯函数（算法地基） =====================

    /**
     * 临期判定：过期日在 [today, today+days] 闭区间内算临期。
     * 已过期（早于 today）、无过期日（null）、超期（晚于 today+days）均不算临期。
     *
     * @param expireDate 过期日，可为 null
     * @param today      今天
     * @param days       临期窗口天数
     */
    public boolean isExpiring(LocalDate expireDate, LocalDate today, int days) {
        if (expireDate == null || today == null) return false;
        // 既未过期（>= today），又在窗口内（<= today+days）
        return !expireDate.isBefore(today) && !expireDate.isAfter(today.plusDays(days));
    }

    /**
     * 不足判定：余量严格小于阈值（等于不算不足）。
     * 都非 null 才比较；任一为 null 返回 false。
     */
    public boolean isLow(BigDecimal amount, BigDecimal threshold) {
        if (amount == null || threshold == null) return false;
        return amount.compareTo(threshold) < 0;
    }

    // ===================== 列表（VO 带食材名/单位名） =====================

    /**
     * 分页查库存（后台管理）：分页查 Pantry 后逐条填食材名/单位名。
     * 参照 IngredientService.pageWithNutrition 范式。
     * 按 update_time 倒序。
     */
    public IPage<PantryVO> page(PageQuery q) {
        IPage<Pantry> page = page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<Pantry>().orderByDesc("update_time"));
        return fillVo(page);
    }

    /**
     * 临期查询：过期日在 [today, today+days] 的库存。按过期日升序（越近越靠前）。
     */
    public List<PantryVO> listExpiring(int days) {
        LocalDate today = LocalDate.now();
        List<Pantry> rows = list(new QueryWrapper<Pantry>()
                .isNotNull("expire_date")
                .ge("expire_date", today)
                .le("expire_date", today.plusDays(days))
                .orderByAsc("expire_date"));
        return fillVoList(rows);
    }

    /**
     * 不足查询：余量严格低于阈值的库存。
     */
    public List<PantryVO> listLow() {
        List<Pantry> rows = list(new QueryWrapper<Pantry>()
                .gt("low_threshold", 0)                       // 阈值为 0 视为不监控
                .apply("amount < low_threshold")
                .orderByAsc("amount"));
        return fillVoList(rows);
    }

    // ===================== 内部辅助 =====================

    /** 把分页 Pantry 转成 PantryVO 分页（保留 total/current/size）。 */
    private IPage<PantryVO> fillVo(IPage<Pantry> page) {
        Map<Long, String> ingName = ingredientNameMap(page.getRecords());
        Map<Long, String> unitName = unitNameMap();
        List<PantryVO> voRecords = page.getRecords().stream()
                .map(p -> toVO(p, ingName, unitName))
                .collect(Collectors.toList());
        Page<PantryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voRecords);
        return result;
    }

    private List<PantryVO> fillVoList(List<Pantry> rows) {
        Map<Long, String> ingName = ingredientNameMap(rows);
        Map<Long, String> unitName = unitNameMap();
        return rows.stream().map(p -> toVO(p, ingName, unitName)).collect(Collectors.toList());
    }

    private PantryVO toVO(Pantry p, Map<Long, String> ingName, Map<Long, String> unitName) {
        PantryVO vo = new PantryVO();
        BeanUtils.copyProperties(p, vo);
        vo.setIngredientName(ingName.get(p.getIngredientId()));
        vo.setUnitName(unitName.get(p.getUnitId()));
        return vo;
    }

    /** 批量取这批库存涉及的食材 id -> name 映射（食材量小，一次性查）。 */
    private Map<Long, String> ingredientNameMap(List<Pantry> rows) {
        List<Long> ids = rows.stream().map(Pantry::getIngredientId).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return new HashMap<>();
        return ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ids))
                .stream().collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
    }

    /** 单位字典（sys_dict group=unit）id -> name。 */
    private Map<Long, String> unitNameMap() {
        return dictMapper.selectList(new QueryWrapper<SysDict>().eq("dict_group", "unit"))
                .stream().collect(Collectors.toMap(SysDict::getId, SysDict::getName, (a, b) -> a));
    }

    /** 单条保存库存（含 grams 换算）。供 controller add 调用，确保走换算。 */
    @org.springframework.transaction.annotation.Transactional
    public void saveWithGrams(Pantry pantry) {
        if (pantry != null) {
            pantry.setGrams(unitConvert == null ? null
                    : unitConvert.toGramsFor(pantry.getIngredientId(), pantry.getAmount(), pantry.getUnitId()));
            save(pantry);
        }
    }

    // ===================== 采购回写（勾选入库） =====================

    /**
     * 采购回写纯函数：按食材构造入库批次 Pantry（不落库），便于单测换算/兜底逻辑。
     *
     * 优先用 amount + unitId 走 {@link #unitConvert} 换算克数；
     * amount/unitId 任一为空时，退回 gramsFallback（采购项的参考克数 referenceGrams），按克入账；
     * 两者都空 → 抛 BizException（上层据 ingredientId 判定后调，正常不会触发）。
     */
    public Pantry planStockUp(Long ingredientId, BigDecimal amount, Long unitId, BigDecimal gramsFallback) {
        if (ingredientId == null) {
            throw new BizException("食材 id 不能为空");
        }
        boolean hasAmountUnit = amount != null && amount.signum() > 0 && unitId != null;
        boolean hasFallback = gramsFallback != null && gramsFallback.signum() > 0;
        Pantry p = new Pantry();
        p.setIngredientId(ingredientId);
        if (hasAmountUnit) {
            p.setAmount(amount);
            p.setUnitId(unitId);
            p.setGrams(unitConvert == null ? null
                    : unitConvert.toGramsFor(ingredientId, amount, unitId));
        } else if (hasFallback) {
            // 兜底：直接用参考克数（采购项 referenceGrams），amount/grams 同值，unitId 留空（克）
            p.setAmount(gramsFallback);
            p.setGrams(gramsFallback);
        } else {
            throw new BizException("无可入库的数量（amount+unitId 或参考克数至少填一项）");
        }
        return p;
    }

    /**
     * 采购回写：按食材入库新批次。采购清单勾选 0→1 时由 {@code ShoppingService.togglePurchased} 调。
     * 只新增不反向扣减（1→0 不回退，库存已消耗）。
     */
    @org.springframework.transaction.annotation.Transactional
    public void stockUpByIngredient(Long ingredientId, BigDecimal amount, Long unitId, BigDecimal gramsFallback) {
        save(planStockUp(ingredientId, amount, unitId, gramsFallback));
    }

    // ===================== 扣减 =====================

    /** 手动扣减库存：从指定 pantry 项扣除 amount，不低于 0。返回扣减后余量。 */
    @org.springframework.transaction.annotation.Transactional
    public BigDecimal deduct(Long id, BigDecimal amount) {
        // 参数校验：id 非空
        if (id == null) {
            throw new BizException("库存项 id 不能为空");
        }
        // 参数校验：amount 非空且 > 0
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("扣减数量必须大于 0");
        }
        Pantry p = getById(id);
        if (p == null) {
            throw new BizException("库存项不存在");
        }
        // 兜底：amount 字段可能为 null（脏数据）
        BigDecimal current = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
        BigDecimal remain = current.subtract(amount);
        if (remain.compareTo(BigDecimal.ZERO) < 0) {
            remain = BigDecimal.ZERO;
        }
        p.setAmount(remain);
        updateById(p);
        return remain;
    }

    // ===================== 按食材 FIFO 扣减（做菜用） =====================

    /**
     * 按食材 FIFO 扣减：查该食材所有 grams>0 的批次（过期日升序、null 最后、id 升序），
     * 逐批扣到 0 为止，扣不动的记 shortage 返回（pantry 不记负）。
     *
     * @return DeductResult 含实扣克数、欠量、各批次明细
     */
    @org.springframework.transaction.annotation.Transactional
    public DeductResult deductByIngredient(Long ingredientId, BigDecimal needGrams) {
        if (ingredientId == null) {
            throw new BizException("食材 id 不能为空");
        }
        if (needGrams == null || needGrams.signum() <= 0) {
            return new DeductResult(ingredientId, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        List<Pantry> batches = list(new QueryWrapper<Pantry>()
                .eq("ingredient_id", ingredientId)
                .gt("grams", 0)
                .last("ORDER BY expire_date IS NULL, expire_date ASC, id ASC"));
        PantryDeductionPlanner.DeductPlan plan = deductionPlanner.plan(batches, needGrams);
        for (PantryDeductionPlanner.BatchDeduction op : plan.ops()) {
            Pantry p = getById(op.pantryId());
            if (p == null) continue;
            p.setGrams(op.remainGrams());
            if (op.newAmount() != null) {
                p.setAmount(op.newAmount());
            }
            updateById(p);
        }
        BigDecimal shortage = plan.shortageGrams();
        BigDecimal deducted = needGrams.subtract(shortage);
        List<DeductResult.BatchOut> outs = plan.ops().stream()
                .map(op -> new DeductResult.BatchOut(op.pantryId(), op.deductGrams(), op.remainGrams()))
                .collect(Collectors.toList());
        return new DeductResult(ingredientId, null, deducted, shortage, outs);
    }

    /** 单食材扣减结果。ingredientName 冗余食材名（由上层批量回填），供前端直接展示。 */
    public record DeductResult(Long ingredientId,
                               String ingredientName,
                               BigDecimal deductedGrams,
                               BigDecimal shortageGrams,
                               List<BatchOut> batches) {
        public record BatchOut(Long pantryId, BigDecimal deductedGrams, BigDecimal remainGrams) {}
    }

    // ===================== 批量添加 =====================

    /** 批量添加库存：按名称匹配食材，未匹配则创建食材后关联。返回成功条数。 */
    @org.springframework.transaction.annotation.Transactional
    public int saveBatch(List<PantryController.BatchItem> items) {
        // 参数校验：items 非空
        if (items == null || items.isEmpty()) {
            throw new BizException("采购内容不能为空");
        }

        // 预加载单位字典
        Map<String, Long> unitNameToId = new HashMap<>();
        if (dictMapper != null) {
            List<SysDict> units = dictMapper.selectList(
                    new QueryWrapper<SysDict>().eq("dict_group", "unit"));
            for (SysDict d : units) unitNameToId.put(d.getName(), d.getId());
        }

        int count = 0;
        for (PantryController.BatchItem item : items) {
            if (item == null) continue;
            if (item.getName() == null || item.getName().isBlank()) continue;
            String name = item.getName().trim();

            // 匹配已有食材
            List<Ingredient> matched = ingredientMapper.selectList(
                    new QueryWrapper<Ingredient>().eq("name", name).last("LIMIT 1"));
            Long ingredientId;
            if (!matched.isEmpty()) {
                ingredientId = matched.get(0).getId();
            } else {
                Ingredient ing = new Ingredient();
                ing.setName(name);
                ingredientMapper.insert(ing);
                ingredientId = ing.getId();
            }

            // 匹配单位：前端传优先，否则用 UnitMatcher 推断
            String unitName = item.getUnit();
            if (unitName == null || unitName.isBlank()) {
                unitName = UnitMatcher.match(name);
            }
            Long unitId = unitNameToId.get(unitName);

            Pantry p = new Pantry();
            p.setIngredientId(ingredientId);
            BigDecimal amt = item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) > 0
                    ? item.getAmount() : BigDecimal.ONE;
            p.setAmount(amt);
            p.setUnitId(unitId);
            p.setExpireDate(item.getExpireDate());
            p.setGrams(unitConvert == null ? null
                    : unitConvert.toGramsFor(ingredientId, amt, unitId));
            save(p);
            count++;
        }

        if (count == 0) {
            throw new BizException("未识别到有效的食材项");
        }
        return count;
    }
}

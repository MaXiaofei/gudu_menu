package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.mapper.IngredientStockMapper;
import com.gudu.xsd.modules.pantry.mapper.PantryMapper;
import com.gudu.xsd.modules.pantry.mapper.StockLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 食材库存服务（V42 手动 3 档语义）。
 *
 * <p>用户手动维护档位（做菜确认用完 / 采购入库 / 手动修正），系统不再自动扣减、不做克数。
 * 真相来源 = ingredient_stock + stock_log；pantry 批次表仅保留给临期通知（listExpiring）。
 *
 * <p>注意：测试 new PantryService(null)，故显式单参构造（@Autowired 主构造）；
 * stockMapper/logMapper 走字段注入，测试中保持 null（新逻辑不触达）。
 */
@Service
public class PantryService extends ServiceImpl<PantryMapper, Pantry> {

    private final IngredientMapper ingredientMapper;
    private IngredientStockMapper stockMapper;
    private StockLogMapper stockLogMapper;

    @Autowired
    public PantryService(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    @Autowired
    public void setStockMapper(IngredientStockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @Autowired
    public void setStockLogMapper(StockLogMapper stockLogMapper) {
        this.stockLogMapper = stockLogMapper;
    }

    // ===================== 档位操作（APP + 管理后台共用） =====================

    /**
     * 直接设档位（采购入库 / 手动入库 / 手动修正 / 管理后台）：upsert ingredient_stock + 写流水（含前后档位）。
     *
     * @param level  ENOUGH / LOW / NONE
     * @param action 流水动作（purchase/manual/…，见 StockLog 常量）
     * @param note   备注（可空）
     * @param refId  溯源（采购入库/撤回 = shopping_item.id，可空）
     */
    @org.springframework.transaction.annotation.Transactional
    public void setLevel(Long ingredientId, String level, String action, String note, Long refId) {
        if (ingredientId == null) throw new BizException("食材 id 不能为空");
        if (!isValidLevel(level)) throw new BizException("库存档位不合法");
        IngredientStock stock = findStock(ingredientId);
        String before = stock == null ? null : stock.getLevel();
        if (stock == null) {
            stock = new IngredientStock();
            stock.setIngredientId(ingredientId);
            stock.setLevel(level);
            stockMapper.insert(stock);
        } else {
            stock.setLevel(level);
            stockMapper.updateById(stock);
        }
        logAction(ingredientId, action, note, before, level, refId);
    }

    /** 做菜确认·用完了 → 设 NONE。 */
    @org.springframework.transaction.annotation.Transactional
    public void useUp(Long ingredientId, String action, String note) {
        setLevel(ingredientId, IngredientStock.LEVEL_NONE, action, note, null);
    }

    /**
     * 做菜确认·用了一些 → 降一档（ENOUGH→LOW）。
     * 降级保护：LOW/NONE 不再降（不会因"用了一些"自动变成用完）；没建档的食材不动。
     */
    @org.springframework.transaction.annotation.Transactional
    public void partialUse(Long ingredientId, String action, String note) {
        if (ingredientId == null) throw new BizException("食材 id 不能为空");
        IngredientStock stock = findStock(ingredientId);
        if (stock == null) return;
        if (IngredientStock.LEVEL_ENOUGH.equals(stock.getLevel())) {
            stock.setLevel(IngredientStock.LEVEL_LOW);
            stockMapper.updateById(stock);
            logAction(ingredientId, action, note, IngredientStock.LEVEL_ENOUGH, IngredientStock.LEVEL_LOW, null);
        }
    }

    /**
     * 删除档位（管理后台删除 = 回到"没建档"；撤回入库新建档也用）：删 ingredient_stock + 记流水（after=null）。
     * 没建档的食材直接返回（无操作）。
     */
    @org.springframework.transaction.annotation.Transactional
    public void removeLevel(Long ingredientId, String action, String note, Long refId) {
        if (ingredientId == null) throw new BizException("食材 id 不能为空");
        IngredientStock stock = findStock(ingredientId);
        if (stock == null) return;
        String before = stock.getLevel();
        stockMapper.deleteById(stock.getId());
        logAction(ingredientId, action, note, before, null, refId);
    }

    /** 手动入库（朋友送/赠品/旧库存补登）：按名匹配/新建食材（无需单位换算）→ 设档位。 */
    @org.springframework.transaction.annotation.Transactional
    public void manualAdd(Long ingredientId, String name, String level, String sourceNote) {
        if ((ingredientId == null) && (name == null || name.isBlank())) {
            throw new BizException("食材 id 和名称至少填一项");
        }
        Ingredient ing;
        if (ingredientId != null) {
            ing = ingredientMapper.selectById(ingredientId);
            if (ing == null) throw new BizException("食材不存在");
        } else {
            List<Ingredient> matched = ingredientMapper.selectList(
                    new QueryWrapper<Ingredient>().eq("name", name.trim()).last("LIMIT 1"));
            if (!matched.isEmpty()) {
                ing = matched.get(0);
            } else {
                ing = new Ingredient();
                ing.setName(name.trim());
                ingredientMapper.insert(ing);
            }
            ingredientId = ing.getId();
        }
        setLevel(ingredientId, level == null ? IngredientStock.LEVEL_ENOUGH : level,
                StockLog.ACTION_MANUAL, sourceNote, null);
    }

    /** 批量读档位（采购清单 badge / 备菜徽标用）：ingredientId → level（没建档的食材不包含）。 */
    public Map<Long, String> levelMap(List<Long> ingredientIds) {
        List<Long> valid = ingredientIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (valid.isEmpty() || stockMapper == null) return Map.of();
        return stockMapper.selectList(new QueryWrapper<IngredientStock>().in("ingredient_id", valid))
                .stream().collect(Collectors.toMap(IngredientStock::getIngredientId,
                        IngredientStock::getLevel, (a, b) -> a));
    }

    // ===================== 列表 / 详情 =====================

    /**
     * 三色分组列表：读 ingredient_stock（每食材一行档位），分 用完/不足/充足 三组 + 汇总，
     * 每项带最近一次变动（stock_log）。APP 库存页 + 管理后台库存页共用。
     * 排序：NONE → LOW → ENOUGH，同状态按食材名。
     */
    public PantryGroupedVO grouped() {
        List<IngredientStock> stocks = stockMapper.selectList(null);
        if (stocks.isEmpty()) {
            PantryGroupedVO vo = new PantryGroupedVO();
            vo.setSummary(new PantryGroupedVO.Summary());
            vo.setItems(List.of());
            return vo;
        }
        List<Long> ids = stocks.stream().map(IngredientStock::getIngredientId).distinct().collect(Collectors.toList());
        Map<Long, Ingredient> ingMap = ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ids))
                .stream().collect(Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));

        List<PantryGroupedVO.Item> items = new java.util.ArrayList<>();
        int enough = 0, low = 0, none = 0;
        for (IngredientStock s : stocks) {
            Ingredient ing = ingMap.get(s.getIngredientId());
            if (ing == null) continue; // 食材被删则跳过
            String level = s.getLevel() == null ? IngredientStock.LEVEL_NONE : s.getLevel();
            switch (level) {
                case IngredientStock.LEVEL_NONE: none++; break;
                case IngredientStock.LEVEL_LOW: low++; break;
                default: enough++; break;
            }
            PantryGroupedVO.Item item = new PantryGroupedVO.Item();
            item.setIngredientId(s.getIngredientId());
            item.setIngredientName(ing.getName());
            item.setLevel(level);
            item.setLastChange(lastChangeVo(s.getIngredientId()));
            items.add(item);
        }
        items.sort(java.util.Comparator
                .comparingInt((PantryGroupedVO.Item it) -> statusOrder(it.getLevel()))
                .thenComparing(it -> it.getIngredientName() == null ? "" : it.getIngredientName(),
                        java.text.Collator.getInstance()));

        PantryGroupedVO vo = new PantryGroupedVO();
        PantryGroupedVO.Summary summary = new PantryGroupedVO.Summary();
        summary.setEnough(enough);
        summary.setLow(low);
        summary.setNone(none);
        vo.setSummary(summary);
        vo.setItems(items);
        return vo;
    }

    /** 食材详情：当前档位 + 最近 6 条流水（stock_log）。 */
    public PantryItemDetailVO itemDetail(Long ingredientId) {
        Ingredient ing = ingredientMapper.selectById(ingredientId);
        if (ing == null) throw new BizException("食材不存在");
        IngredientStock stock = findStock(ingredientId);
        PantryItemDetailVO vo = new PantryItemDetailVO();
        vo.setIngredientId(ingredientId);
        vo.setIngredientName(ing.getName());
        vo.setLevel(stock == null ? IngredientStock.LEVEL_NONE : stock.getLevel());
        vo.setChanges(logList(ingredientId, 6));
        return vo;
    }

    // ===================== 临期通知（pantry 批次表遗留，通知调度用） =====================

    /** 临期查询：过期日在 [today, today+days] 的库存（通知调度用，读 pantry 批次表）。 */
    public List<PantryVO> listExpiring(int days) {
        LocalDate today = LocalDate.now();
        List<Pantry> rows = list(new QueryWrapper<Pantry>()
                .isNotNull("expire_date")
                .ge("expire_date", today)
                .le("expire_date", today.plusDays(days))
                .orderByAsc("expire_date"));
        return rows.stream().map(p -> {
            PantryVO vo = new PantryVO();
            vo.setId(p.getId());
            vo.setIngredientId(p.getIngredientId());
            vo.setAmount(p.getAmount());
            vo.setUnitId(p.getUnitId());
            vo.setExpireDate(p.getExpireDate());
            vo.setStorage(p.getStorage());
            vo.setUpdateTime(p.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 临期判定纯函数（listExpiring 用）。 */
    public boolean isExpiring(LocalDate expireDate, LocalDate today, int days) {
        if (expireDate == null || today == null) return false;
        return !expireDate.isBefore(today) && !expireDate.isAfter(today.plusDays(days));
    }

    // ===================== 内部辅助 =====================

    private IngredientStock findStock(Long ingredientId) {
        return stockMapper.selectOne(new QueryWrapper<IngredientStock>().eq("ingredient_id", ingredientId));
    }

    private boolean isValidLevel(String level) {
        return IngredientStock.LEVEL_ENOUGH.equals(level)
                || IngredientStock.LEVEL_LOW.equals(level)
                || IngredientStock.LEVEL_NONE.equals(level);
    }

    private void logAction(Long ingredientId, String action, String note,
                           String beforeLevel, String afterLevel, Long refId) {
        if (stockLogMapper == null) return;
        StockLog log = new StockLog();
        log.setIngredientId(ingredientId);
        log.setAction(action);
        log.setBeforeLevel(beforeLevel);
        log.setAfterLevel(afterLevel);
        log.setNote(note);
        log.setRefId(refId);
        stockLogMapper.insert(log);
    }

    private List<StockLog> logList(Long ingredientId, int limit) {
        if (stockLogMapper == null) return List.of();
        return stockLogMapper.selectList(new QueryWrapper<StockLog>()
                .eq("ingredient_id", ingredientId)
                .orderByDesc("create_time").last("LIMIT " + limit));
    }

    /** 最近一次变动（无记录返回 null）。 */
    private PantryGroupedVO.LastChange lastChangeVo(Long ingredientId) {
        List<StockLog> rows = logList(ingredientId, 1);
        if (rows.isEmpty()) return null;
        StockLog log = rows.get(0);
        PantryGroupedVO.LastChange lc = new PantryGroupedVO.LastChange();
        lc.setSource(log.getAction());
        lc.setSourceNote(log.getNote());
        lc.setCreateTime(log.getCreateTime());
        return lc;
    }

    /** 排序用：NONE=0, LOW=1, ENOUGH=2。 */
    private int statusOrder(String level) {
        return switch (level) {
            case IngredientStock.LEVEL_NONE -> 0;
            case IngredientStock.LEVEL_LOW -> 1;
            default -> 2;
        };
    }
}

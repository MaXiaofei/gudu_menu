package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.modules.pantry.mapper.PantryChangeLogMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存变动流水服务：写流水 + 查最近 N 条。
 * 每次 pantry 变动（做菜/采购/盘点/手动添加）调用 log() 记一笔，支撑列表来源标签和详情页明细。
 */
@Service
public class PantryChangeLogService extends ServiceImpl<PantryChangeLogMapper, PantryChangeLog> {

    /**
     * 记一笔变动流水。
     *
     * @param ingredientId 食材
     * @param source       来源（cook/purchase/inventory/manual，见 PantryChangeLog 常量）
     * @param deltaGrams   变动量（正入负出，克）
     * @param amountAfter  变动后该食材合计（克，可空）
     * @param sourceNote   来源备注（手动：朋友送/赠品；可空）
     */
    public void log(Long ingredientId, String source, BigDecimal deltaGrams, BigDecimal amountAfter, String sourceNote) {
        PantryChangeLog entry = new PantryChangeLog();
        entry.setIngredientId(ingredientId);
        entry.setSource(source);
        entry.setDelta(deltaGrams);
        entry.setAmountAfter(amountAfter);
        entry.setSourceNote(sourceNote);
        save(entry);
    }

    /** 查某食材最近 N 条变动流水（按时间倒序）。 */
    public List<PantryChangeLog> listRecent(Long ingredientId, int limit) {
        return list(new QueryWrapper<PantryChangeLog>()
                .eq("ingredient_id", ingredientId)
                .orderByDesc("create_time")
                .last("LIMIT " + limit));
    }

    /** 查某食材最近 1 条变动（用于列表行展示来源标签）。 */
    public PantryChangeLog lastOne(Long ingredientId) {
        List<PantryChangeLog> rows = listRecent(ingredientId, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }
}

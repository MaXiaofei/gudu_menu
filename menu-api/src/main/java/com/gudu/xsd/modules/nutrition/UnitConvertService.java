package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientUnitGramMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单位换算服务（评审 B · 最高杠杆地基）。
 *
 * <p>克为内部记账基准。toGrams 是纯函数（可单测）：
 * 克单位直通（1g=1g，无需为每食材建"克=1.0"冗余行）；
 * 非克单位按 gramsPerUnit 算；未配置返回 null（兜底，不硬算）。
 *
 * <p>参照 PantryService 范式：纯函数算法地基 + 显式 Mapper 构造。
 * 测试 new UnitConvertService(Set.of(...))（mapper=null 不触达）：故双构造。
 */
@Service
public class UnitConvertService {

    private final IngredientUnitGramMapper unitGramMapper;
    private final DictMapper dictMapper;
    private Set<Long> gramUnitIds = new HashSet<>();

    /** 运行期构造（Spring 注入两个 Mapper）。 */
    public UnitConvertService(IngredientUnitGramMapper unitGramMapper, DictMapper dictMapper) {
        this.unitGramMapper = unitGramMapper;
        this.dictMapper = dictMapper;
    }

    /** 测试构造：只传 gramUnitIds（纯函数测试，mapper=null 不触达）。 */
    public UnitConvertService(Set<Long> gramUnitIds) {
        this.unitGramMapper = null;
        this.dictMapper = null;
        this.gramUnitIds = gramUnitIds == null ? new HashSet<>() : new HashSet<>(gramUnitIds);
    }

    /** 启动时缓存 'g'/'克' 的 unit id（非克单位判定用）。 */
    @PostConstruct
    void initGramUnitIds() {
        if (dictMapper == null) return;
        List<SysDict> grams = dictMapper.selectList(
                new QueryWrapper<SysDict>().eq("dict_group", "unit").in("name", List.of("g", "克")));
        gramUnitIds.clear();
        grams.forEach(d -> gramUnitIds.add(d.getId()));
    }

    /** unitId 是否为"克"单位。 */
    public boolean isGramUnit(Long unitId) {
        return unitId != null && gramUnitIds.contains(unitId);
    }

    /**
     * 纯函数：amount + unitId + gramsPerUnit → 克。
     * 克单位直通；非克按系数；未配置(amount/unitId/gramsPerUnit 缺)返回 null。
     */
    public BigDecimal toGrams(BigDecimal amount, Long unitId, BigDecimal gramsPerUnit) {
        if (amount == null) return null;
        if (isGramUnit(unitId)) return amount;
        if (gramsPerUnit == null) return null;
        return amount.multiply(gramsPerUnit);
    }

    /** 查表：「食材 × 单位」每单位克数；未配置返回 null。 */
    public BigDecimal gramsPerUnit(Long ingredientId, Long unitId) {
        if (unitGramMapper == null || ingredientId == null || unitId == null) return null;
        IngredientUnitGram row = unitGramMapper.selectOne(
                new QueryWrapper<IngredientUnitGram>()
                        .eq("ingredient_id", ingredientId)
                        .eq("unit_id", unitId));
        return row == null ? null : row.getGramsPerUnit();
    }

    /** 该食材默认单位（is_default=1）的每单位克数；未配置返回 null。 */
    public BigDecimal defaultGramsPerUnit(Long ingredientId) {
        if (unitGramMapper == null || ingredientId == null) return null;
        IngredientUnitGram row = unitGramMapper.selectOne(
                new QueryWrapper<IngredientUnitGram>()
                        .eq("ingredient_id", ingredientId)
                        .eq("is_default", 1));
        return row == null ? null : row.getGramsPerUnit();
    }

    /** 薄封装：查表 + 纯函数换算。保存路径调用此方法。 */
    public BigDecimal toGramsFor(Long ingredientId, BigDecimal amount, Long unitId) {
        return toGrams(amount, unitId, gramsPerUnit(ingredientId, unitId));
    }
}

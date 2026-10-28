package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientIconPolicy;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientNameNormalizer;
import com.gudu.xsd.common.tenant.DishVisibility;
import com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import com.gudu.xsd.modules.nutrition.mapper.NutritionMetricMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientService extends ServiceImpl<IngredientMapper, Ingredient> {

    private final IngredientNutritionMapper nutMapper;
    private final NutritionMetricMapper metricMapper;

    /** 采购品类字典（运行期由 setter 注入；测试中为 null 时跳过品类名填充）。 */
    private DictMapper dictMapper;

    @Autowired
    public void setDictMapper(DictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    /** 别名表（V95；测试中为 null 时搜索只匹配名称）。 */
    private IngredientAliasMapper aliasMapper;

    @Autowired
    public void setAliasMapper(IngredientAliasMapper aliasMapper) {
        this.aliasMapper = aliasMapper;
    }

    /** 新增食材：按名称/品类计算插画 key（V68）。 */
    @Override
    public boolean save(Ingredient ing) {
        fillIconKey(ing);
        return super.save(ing);
    }

    /** 更新食材：改了名称或品类时重算插画 key；两者都没带则不动 icon_key。 */
    @Override
    public boolean updateById(Ingredient ing) {
        if (ing != null && (ing.getName() != null || ing.getPurchaseCategoryId() != null)) {
            Ingredient old = ing.getId() == null ? null : getById(ing.getId());
            String name = ing.getName() != null ? ing.getName() : (old == null ? null : old.getName());
            Long categoryId = ing.getPurchaseCategoryId() != null ? ing.getPurchaseCategoryId()
                    : (old == null ? null : old.getPurchaseCategoryId());
            ing.setIconKey(IngredientIconPolicy.resolve(name, categoryNameOf(categoryId)));
        }
        return super.updateById(ing);
    }

    /** 按名称 + 品类写入插画 key（调用方在 insert 前使用，如 pantry 手动入库新建食材）。 */
    public void fillIconKey(Ingredient ing) {
        if (ing == null) return;
        ing.setIconKey(IngredientIconPolicy.resolve(ing.getName(), categoryNameOf(ing.getPurchaseCategoryId())));
    }

    /** 食材 id → 插画 key（只含非空；VO 批量回填用）。 */
    public Map<Long, String> iconKeyMap(java.util.Collection<Long> ingredientIds) {
        Map<Long, String> out = new HashMap<>();
        if (ingredientIds == null || ingredientIds.isEmpty()) return out;
        List<Long> ids = ingredientIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return out;
        for (Ingredient ing : listByIds(ids)) {
            if (ing.getIconKey() != null) out.put(ing.getId(), ing.getIconKey());
        }
        return out;
    }

    private String categoryNameOf(Long purchaseCategoryId) {
        if (purchaseCategoryId == null) return null;
        return categoryNameMap().get(purchaseCategoryId); // 复用品类字典查询（食材写入低频）
    }

    /** 按名称精确查未删除食材（逻辑删自动过滤）；不存在返回 null。 */
    public Ingredient findByName(String name) {
        if (name == null || name.isBlank()) return null;
        return list(new QueryWrapper<Ingredient>().eq("name", name).orderByAsc("id").last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    /** 保存食材，并整体替换其营养 EAV（新增/复用同一路径）。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveWithNutrition(Ingredient ing, List<IngredientNutrition> nuts) {
        save(ing);
        replaceNutrition(ing.getId(), nuts);
    }

    /**
     * 仅整体替换某食材的营养 EAV（不重 save 食材本身，适用于食材已存在、仅更新营养的场景，如 AI 营养补全）。
     * 防止 saveWithNutrition 对已存在 id 的食材执行 save → 主键冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceNutrition(Long ingredientId, List<IngredientNutrition> nuts) {
        nutMapper.delete(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ingredientId));
        for (IngredientNutrition n : nuts) {
            n.setId(null);
            n.setIngredientId(ingredientId);
            nutMapper.insert(n);
        }
    }

    /** 返回该食材各营养指标 per 100g 的值：metricId -> value。 */
    public Map<Long, BigDecimal> nutritionOf(Long ingredientId) {
        return nutMapper.selectList(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ingredientId))
                .stream()
                .collect(Collectors.toMap(IngredientNutrition::getMetricId, IngredientNutrition::getValue));
    }

    /**
     * 批量查询多个食材的营养 EAV（一次 IN 查询，消除 N+1）。
     * 返回 ingredientId -> (metricId -> value)。未命中食材不出现在 map 中（调用方按需补空）。
     * 入参为空时直接返回空 map，避免拼出非法的 `IN ()`。
     */
    public Map<Long, Map<Long, BigDecimal>> nutritionBatch(List<Long> ingredientIds) {
        Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return result;
        }
        nutMapper.selectList(new QueryWrapper<IngredientNutrition>().in("ingredient_id", ingredientIds))
                .forEach(n -> result
                        .computeIfAbsent(n.getIngredientId(), k -> new HashMap<>())
                        .put(n.getMetricId(), n.getValue()));
        return result;
    }

    /**
     * 列表（含营养）：一次查 metric 字典建 id->name 映射，再对结果食材做一次 IN 批量取营养 EAV，
     * 将 metricId->value 转成 metric name->value（per 100g），便于前端直接做中文映射展示。
     * 整体 2 次 DB 查询（metric 字典 + 营养批量），不再逐食材查。
     *
     * @param purchaseCategoryId 采购分类 id，null 表示全部
     * @param keyword 名称模糊搜索关键词，null/空 表示不过滤
     */
    public List<IngredientVO> listWithNutrition(Long purchaseCategoryId, String keyword) {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        // 权重排序：usage_count 倒序（用得多的靠前），同分再按 id 倒序稳定
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>()
                .orderByDesc("usage_count").orderByDesc("id");
        if (purchaseCategoryId != null) {
            qw.eq("purchase_category_id", purchaseCategoryId);
        }
        Map<Long, String> aliasHits = applyKeyword(qw, keyword);
        List<Ingredient> ings = preferFamilyFirst(list(qw));
        return fillAliasHits(toVOList(ings, metricNameById), aliasHits);
    }

    /** 食材详情（编辑页：名称/品类/食用属性 + 营养）。 */
    public IngredientVO detail(Long id) {
        Ingredient ing = getById(id);
        if (ing == null) {
            return null;
        }
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        Map<Long, String> categoryName = categoryNameMap();
        IngredientVO vo = toVO(ing, metricNameById, nutritionOf(id));
        vo.setCategoryName(categoryName.get(ing.getPurchaseCategoryId()));
        return vo;
    }

    /**
     * 分页列表（含营养，后台管理）：分页查食材实体后，逐条填营养 VO。
     * IPage 的 records 转成 VO 后塞回同一 IPage（total/current/size 不变）。
     * 支持 keyword(名称 LIKE) + purchaseCategoryId 双筛选；默认按 usage_count 权重倒序。
     */
    public IPage<IngredientVO> pageWithNutrition(IngredientPageQuery q) {
        Map<Long, String> metricNameById = metricMapper.selectList(null).stream()
                .collect(Collectors.toMap(NutritionMetric::getId, NutritionMetric::getName));
        // 权重排序：usage_count 倒序（用户用得多的靠前），同分再按 id 倒序稳定
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>()
                .orderByDesc("usage_count").orderByDesc("id");
        if (q.getPurchaseCategoryId() != null) {
            qw.eq("purchase_category_id", q.getPurchaseCategoryId());
        }
        Map<Long, String> aliasHits = applyKeyword(qw, q.getKeyword());
        if (Boolean.TRUE.equals(q.getFamilyOnly())) {
            Long familyId = DishVisibility.effectiveFamilyId();
            if (familyId == null) qw.apply("1 = 0");
            else qw.eq("family_id", familyId);
        }
        IPage<Ingredient> page = page(new Page<>(q.getPageNum(), q.getPageSize()), qw);
        List<IngredientVO> voRecords = fillAliasHits(
                toVOList(preferFamilyFirst(page.getRecords()), metricNameById), aliasHits);
        // IPage 是接口，直接转换 records；用 setRecords 保留分页元信息
        Page<IngredientVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voRecords);
        return result;
    }

    /**
     * 关键词条件：名称包含关键词，或关键词命中某个别名（搜「土豆」出「马铃薯」）。
     * 返回命中的 ingredientId -> 别名，供 VO 标注 matchedAlias。可见性（公共 + 本家）由 MyBatis 拦截器兜底。
     */
    private Map<Long, String> applyKeyword(QueryWrapper<Ingredient> qw, String keyword) {
        Map<Long, String> hits = new HashMap<>();
        if (keyword == null || keyword.trim().isEmpty()) return hits;
        String kw = IngredientNameNormalizer.normalize(keyword);
        if (aliasMapper != null) {
            for (IngredientAlias a : aliasMapper.selectList(
                    new QueryWrapper<IngredientAlias>().like("alias", kw).last("LIMIT 200"))) {
                hits.putIfAbsent(a.getIngredientId(), a.getAlias());
            }
        }
        if (hits.isEmpty()) {
            qw.like("name", kw);
        } else {
            List<Long> ids = new java.util.ArrayList<>(hits.keySet());
            qw.and(w -> w.like("name", kw).or().in("id", ids));
        }
        return hits;
    }

    /** 命中别名的 VO 标注 matchedAlias（名称本身已包含关键词的不标）。 */
    private List<IngredientVO> fillAliasHits(List<IngredientVO> vos, Map<Long, String> hits) {
        if (hits.isEmpty()) return vos;
        for (IngredientVO vo : vos) vo.setMatchedAlias(hits.get(vo.getId()));
        return vos;
    }

    /** 同名（公共 + 本家私有）时本家的排前面；不同名的相对顺序不变。 */
    static List<Ingredient> preferFamilyFirst(List<Ingredient> in) {
        Map<String, List<Integer>> slots = new HashMap<>();
        for (int i = 0; i < in.size(); i++) {
            String n = in.get(i).getName();
            if (n != null) slots.computeIfAbsent(n, k -> new java.util.ArrayList<>()).add(i);
        }
        List<Ingredient> out = new java.util.ArrayList<>(in);
        for (List<Integer> idx : slots.values()) {
            if (idx.size() < 2) continue;
            List<Ingredient> group = idx.stream().map(in::get)
                    .sorted(java.util.Comparator.comparing(i -> i.getFamilyId() == null)).toList();
            for (int k = 0; k < idx.size(); k++) out.set(idx.get(k), group.get(k));
        }
        return out;
    }

    /** 实体列表 → VO 列表：营养/品类名均批量预取，一次 IN 查询各一次。 */
    private List<IngredientVO> toVOList(List<Ingredient> ings, Map<Long, String> metricNameById) {
        if (ings.isEmpty()) {
            return List.of();
        }
        List<Long> ids = ings.stream().map(Ingredient::getId).collect(Collectors.toList());
        // 一次 IN 批量取全部营养，消除 N+1
        Map<Long, Map<Long, BigDecimal>> nutByIngredient = nutritionBatch(ids);
        Map<Long, String> categoryName = categoryNameMap();
        return ings.stream()
                .map(ing -> toVO(ing, metricNameById,
                        nutByIngredient.getOrDefault(ing.getId(), java.util.Collections.emptyMap())))
                .peek(vo -> vo.setCategoryName(categoryName.get(vo.getPurchaseCategoryId())))
                .collect(Collectors.toList());
    }

    /**
     * 组装 VO：拷贝食材字段，把预取的 metricId->value 映射成 metric name->value。
     * 营养/品类名均由调用方批量预取后传入，不在本方法内再查 DB。
     */
    private IngredientVO toVO(Ingredient ing, Map<Long, String> metricNameById,
                              Map<Long, BigDecimal> metricIdToValue) {
        IngredientVO vo = new IngredientVO();
        BeanUtils.copyProperties(ing, vo);
        vo.setFamilyOwned(ing.getFamilyId() != null);
        Map<String, BigDecimal> named = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> e : metricIdToValue.entrySet()) {
            String name = metricNameById.get(e.getKey());
            if (name != null) {
                named.put(name, e.getValue());
            }
        }
        vo.setNutrition(named);
        return vo;
    }

    /** 别名 -> 公共食材名（拍照识别 / 名称匹配用；别名只指向公共食材）。 */
    public Map<String, String> aliasToNameMap() {
        Map<String, String> out = new HashMap<>();
        if (aliasMapper == null) return out;
        List<IngredientAlias> all = aliasMapper.selectList(null);
        if (all.isEmpty()) return out;
        Map<Long, String> names = listByIds(all.stream().map(IngredientAlias::getIngredientId).distinct().toList())
                .stream().filter(i -> i.getFamilyId() == null)
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
        for (IngredientAlias a : all) {
            String n = names.get(a.getIngredientId());
            if (n != null) out.putIfAbsent(a.getAlias(), n);
        }
        return out;
    }

    /** 采购品类 id -> 名称（运营收编页展示各家所选分类分布用）。 */
    public Map<Long, String> categoryNames() {
        return categoryNameMap();
    }

    /** 采购品类字典（sys_dict group=purchase_category）id -> name。 */
    private Map<Long, String> categoryNameMap() {
        if (dictMapper == null) return new HashMap<>();
        return dictMapper.selectList(new QueryWrapper<SysDict>().eq("dict_group", "purchase_category"))
                .stream().collect(Collectors.toMap(SysDict::getId, SysDict::getName, (a, b) -> a));
    }
}

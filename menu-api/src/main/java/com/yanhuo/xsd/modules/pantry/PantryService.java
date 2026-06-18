package com.yanhuo.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.common.PageQuery;
import com.yanhuo.xsd.modules.dict.SysDict;
import com.yanhuo.xsd.modules.dict.mapper.DictMapper;
import com.yanhuo.xsd.modules.nutrition.Ingredient;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.yanhuo.xsd.modules.pantry.mapper.PantryMapper;
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
}

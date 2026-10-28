package com.gudu.xsd.modules.nutrition.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientAlias;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.AdoptResult;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.AliasItem;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.CategoryShare;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.Group;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.PageResult;
import com.gudu.xsd.modules.nutrition.domain.repository.IngredientReferences;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientNameNormalizer;
import com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 运营收编（管理后台「家庭新建食材」）：
 * 按规整后名称汇总所有家庭私有食材；「收编为公共食材」/「合并到已有公共食材」把同名私有食材合并到一个公共食材，
 * 各家写法与目标名不同的记为别名；别名的列表 / 新增 / 删除。写操作都落管理后台操作日志。
 * 平台级操作：以系统模式运行（绕过家庭可见性），合并的 SQL 由 {@link IngredientReferences} 在单事务内完成。
 */
@Service
@RequiredArgsConstructor
public class IngredientAdoptionAppService {

    private final IngredientService ingredients;
    private final IngredientAliasMapper aliases;
    private final IngredientReferences references;
    private final IngredientCatalogAppService catalog;
    private final OperationLogRepository logs;
    private final Clock clock;

    // ------------------------------------------------------------ 汇总

    /** 家庭私有食材按规整名称汇总：家庭数降序，其次名称。 */
    public List<Group> groups() {
        List<Ingredient> all = asSystem(() -> ingredients.list(
                new QueryWrapper<Ingredient>().isNotNull("family_id").orderByAsc("id")));
        Map<Long, String> catNames = ingredients.categoryNames();
        Map<String, List<Ingredient>> byName = new LinkedHashMap<>();
        for (Ingredient i : all) {
            byName.computeIfAbsent(IngredientNameNormalizer.normalize(i.getName()), k -> new ArrayList<>()).add(i);
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<Ingredient>> e : byName.entrySet()) {
            List<Ingredient> list = e.getValue();
            Map<Long, Integer> cat = new LinkedHashMap<>();
            for (Ingredient i : list) cat.merge(i.getPurchaseCategoryId() == null ? -1L : i.getPurchaseCategoryId(), 1, Integer::sum);
            List<CategoryShare> shares = cat.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .map(c -> new CategoryShare(c.getKey() < 0 ? null : c.getKey(),
                            c.getKey() < 0 ? "未分类" : catNames.getOrDefault(c.getKey(), "分类" + c.getKey()), c.getValue()))
                    .toList();
            Long suggested = shares.stream().filter(s -> s.categoryId() != null).map(CategoryShare::categoryId)
                    .findFirst().orElse(null);
            LocalDateTime earliest = list.stream().map(Ingredient::getCreateTime).filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            int families = (int) list.stream().map(Ingredient::getFamilyId).distinct().count();
            out.add(new Group(e.getKey(), families, list.size(), earliest, shares, suggested,
                    list.stream().map(Ingredient::getId).toList()));
        }
        out.sort(Comparator.comparingInt(Group::familyCount).reversed().thenComparing(Group::name));
        return out;
    }

    // ------------------------------------------------------------ 收编 / 合并

    /** 操作 A：新建一个公共食材（运营确认的名称与分类），把同名私有食材全部合并过去。 */
    @Transactional(rollbackFor = Exception.class)
    public AdoptResult adopt(Long operatorId, String groupName, String finalName, Long categoryId, String reason) {
        List<Ingredient> sources = sourcesOf(groupName);
        String name = IngredientNameNormalizer.normalize(finalName == null || finalName.isBlank() ? groupName : finalName);
        if (name.isEmpty()) throw new BizException("食材名称不能为空");
        if (asSystem(() -> catalog.findPublicByNameOrAlias(name)) != null) {
            throw new BizException("已有公共食材或别名「" + name + "」，请改用「合并到已有公共食材」");
        }
        Ingredient target = new Ingredient();
        target.setName(name);
        target.setFamilyId(null);
        target.setPurchaseCategoryId(categoryId != null ? categoryId : sources.get(0).getPurchaseCategoryId());
        target.setEdible(sources.get(0).getEdible() == null ? 1 : sources.get(0).getEdible());
        asSystem(() -> ingredients.save(target));
        AdoptResult r = mergeAll(sources, target);
        log(operatorId, OperationAction.ADOPT_INGREDIENT, target.getId(), reason,
                "收编「" + groupName + "」为公共食材「" + name + "」，合并 " + r.mergedIngredients() + " 个家庭食材");
        return r;
    }

    /** 操作 B：把同名私有食材合并到已有的公共食材，名称记为别名。 */
    @Transactional(rollbackFor = Exception.class)
    public AdoptResult mergeInto(Long operatorId, String groupName, Long targetId, String reason) {
        List<Ingredient> sources = sourcesOf(groupName);
        Ingredient target = asSystem(() -> ingredients.getById(targetId));
        if (target == null || target.getFamilyId() != null) throw new BizException("目标必须是已有的公共食材");
        AdoptResult r = mergeAll(sources, target);
        log(operatorId, OperationAction.MERGE_INGREDIENT, target.getId(), reason,
                "「" + groupName + "」合并到公共食材「" + target.getName() + "」，合并 " + r.mergedIngredients() + " 个家庭食材");
        return r;
    }

    private List<Ingredient> sourcesOf(String groupName) {
        String key = IngredientNameNormalizer.normalize(groupName);
        List<Ingredient> sources = asSystem(() -> ingredients.list(new QueryWrapper<Ingredient>()
                .isNotNull("family_id").orderByAsc("id"))).stream()
                .filter(i -> key.equals(IngredientNameNormalizer.normalize(i.getName()))).toList();
        if (sources.isEmpty()) throw new BizException("没有找到名为「" + key + "」的家庭食材");
        return sources;
    }

    private AdoptResult mergeAll(List<Ingredient> sources, Ingredient target) {
        IngredientReferences.MergeStats stats = references.merge(
                sources.stream().map(Ingredient::getId).toList(), target.getId());
        // 各家写法与目标名不同的记为别名
        Set<String> added = new HashSet<>();
        List<String> addedList = new ArrayList<>();
        for (Ingredient s : sources) {
            String alias = IngredientNameNormalizer.normalize(s.getName());
            if (!alias.equals(target.getName()) && added.add(alias) && tryAddAlias(alias, target)) addedList.add(alias);
            asSystem(() -> ingredients.removeById(s.getId())); // 被合并的私有食材软删
        }
        return new AdoptResult(target.getId(), target.getName(), sources.size(), stats.totalMoved(),
                stats.totalMerged(), addedList);
    }

    /** 自动记别名：与公共名 / 已有别名冲突则静默跳过。 */
    private boolean tryAddAlias(String alias, Ingredient target) {
        if (alias.isEmpty() || alias.length() > 64) return false;
        if (asSystem(() -> catalog.findPublicByNameOrAlias(alias)) != null) return false;
        IngredientAlias a = new IngredientAlias();
        a.setAlias(alias);
        a.setIngredientId(target.getId());
        a.setCreateTime(LocalDateTime.now(clock));
        aliases.insert(a);
        return true;
    }

    // ------------------------------------------------------------ 别名维护

    public PageResult<AliasItem> aliasPage(String keyword, int pageNum, int pageSize) {
        QueryWrapper<IngredientAlias> qw = new QueryWrapper<IngredientAlias>().orderByDesc("id");
        if (keyword != null && !keyword.isBlank()) {
            String kw = IngredientNameNormalizer.normalize(keyword);
            List<Long> ids = asSystem(() -> ingredients.list(new QueryWrapper<Ingredient>()
                    .isNull("family_id").like("name", kw).select("id"))).stream().map(Ingredient::getId).toList();
            qw.and(w -> {
                w.like("alias", kw);
                if (!ids.isEmpty()) w.or().in("ingredient_id", ids);
            });
        }
        Page<IngredientAlias> page = aliases.selectPage(new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), qw);
        Map<Long, String> names = nameMap(page.getRecords().stream().map(IngredientAlias::getIngredientId).distinct().toList());
        return new PageResult<>(page.getRecords().stream().map(a -> new AliasItem(a.getId(), a.getAlias(),
                a.getIngredientId(), names.get(a.getIngredientId()), a.getCreateTime())).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public AliasItem addAlias(Long operatorId, String rawAlias, Long ingredientId) {
        String alias = IngredientNameNormalizer.normalize(rawAlias);
        if (alias.isEmpty()) throw new BizException("别名不能为空");
        if (alias.length() > 64) throw new BizException("别名不超过 64 字");
        Ingredient target = ingredientId == null ? null : asSystem(() -> ingredients.getById(ingredientId));
        if (target == null || target.getFamilyId() != null) throw new BizException("别名只能指向公共食材");
        if (alias.equals(target.getName())) throw new BizException("别名与食材名相同，无需添加");
        Ingredient clash = asSystem(() -> catalog.findPublicByNameOrAlias(alias));
        if (clash != null) throw new BizException("「" + alias + "」已是公共食材「" + clash.getName() + "」的名称或别名");
        IngredientAlias a = new IngredientAlias();
        a.setAlias(alias);
        a.setIngredientId(ingredientId);
        a.setCreateTime(LocalDateTime.now(clock));
        aliases.insert(a);
        log(operatorId, OperationAction.ADD_INGREDIENT_ALIAS, ingredientId, null,
                "「" + alias + "」→「" + target.getName() + "」");
        return new AliasItem(a.getId(), alias, ingredientId, target.getName(), a.getCreateTime());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAlias(Long operatorId, Long aliasId) {
        IngredientAlias a = aliasId == null ? null : aliases.selectById(aliasId);
        if (a == null) throw BizException.notFound("别名不存在");
        aliases.deleteById(aliasId);
        log(operatorId, OperationAction.DEL_INGREDIENT_ALIAS, a.getIngredientId(), null, "删除别名「" + a.getAlias() + "」");
    }

    private Map<Long, String> nameMap(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return asSystem(() -> ingredients.listByIds(ids)).stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (x, y) -> x));
    }

    private void log(Long operatorId, OperationAction action, Long targetId, String reason, String detail) {
        logs.save(OperationLog.record(operatorId, action, targetId, reason, detail, LocalDateTime.now(clock)));
    }

    private static <T> T asSystem(Supplier<T> body) {
        return TenantContextHolder.callAsSystem(body);
    }
}

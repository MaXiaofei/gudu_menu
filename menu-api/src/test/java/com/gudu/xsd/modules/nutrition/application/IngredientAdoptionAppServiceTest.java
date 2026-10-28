package com.gudu.xsd.modules.nutrition.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientAlias;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.AdoptResult;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.Group;
import com.gudu.xsd.modules.nutrition.domain.repository.IngredientReferences;
import com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 运营收编：汇总、收编为公共、合并到已有公共、别名维护与操作日志。 */
class IngredientAdoptionAppServiceTest {

    private final IngredientService ingredients = mock(IngredientService.class);
    private final IngredientAliasMapper aliases = mock(IngredientAliasMapper.class);
    private final IngredientReferences references = mock(IngredientReferences.class);
    private final IngredientCatalogAppService catalog = mock(IngredientCatalogAppService.class);
    private final OperationLogRepository logs = mock(OperationLogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneId.of("UTC"));
    private IngredientAdoptionAppService svc;

    @BeforeEach
    void setUp() {
        svc = new IngredientAdoptionAppService(ingredients, aliases, references, catalog, logs, clock);
        when(ingredients.categoryNames()).thenReturn(Map.of(1L, "蔬菜", 2L, "调味料"));
    }

    private static Ingredient priv(Long id, String name, Long family, Long cat, LocalDateTime at) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setFamilyId(family);
        i.setPurchaseCategoryId(cat);
        i.setCreateTime(at);
        return i;
    }

    private void givenPrivates() {
        when(ingredients.list(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(List.of(
                priv(11L, "青花椒油", 1L, 2L, LocalDateTime.parse("2026-10-20T10:00:00")),
                priv(12L, "青花椒油 ", 2L, 2L, LocalDateTime.parse("2026-10-18T10:00:00")),
                priv(13L, "青花椒油", 3L, 1L, LocalDateTime.parse("2026-10-25T10:00:00")),
                priv(14L, "家传酱", 1L, 1L, null)));
    }

    @Test
    void 汇总_按规整名称合并_家庭数降序_带最早时间与分类分布() {
        givenPrivates();

        List<Group> groups = svc.groups();

        assertThat(groups).extracting(Group::name).containsExactly("青花椒油", "家传酱");
        Group g = groups.get(0);
        assertThat(g.familyCount()).isEqualTo(3);
        assertThat(g.earliest()).isEqualTo(LocalDateTime.parse("2026-10-18T10:00:00"));
        assertThat(g.categories()).extracting("categoryName", "count").containsExactly(
                org.assertj.core.groups.Tuple.tuple("调味料", 2), org.assertj.core.groups.Tuple.tuple("蔬菜", 1));
        assertThat(g.suggestedCategoryId()).as("默认取多数家庭所选").isEqualTo(2L);
        assertThat(g.ingredientIds()).containsExactly(11L, 12L, 13L);
    }

    @Test
    void 收编为公共食材_建公共食材_合并全部同名私有_写别名与日志() {
        givenPrivates();
        when(catalog.findPublicByNameOrAlias(any())).thenReturn(null);
        doAnswer(inv -> {
            ((Ingredient) inv.getArgument(0)).setId(100L);
            return true;
        }).when(ingredients).save(any());
        when(references.merge(any(), any())).thenReturn(new IngredientReferences.MergeStats(
                new java.util.LinkedHashMap<>(Map.of("dish_ingredient", 3)), new java.util.LinkedHashMap<>(Map.of("ingredient_stock", 1))));

        AdoptResult r = svc.adopt(9L, "青花椒油", "青花椒油", 2L, "常见调料");

        ArgumentCaptor<Ingredient> cap = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredients).save(cap.capture());
        assertThat(cap.getValue().getFamilyId()).as("收编后是公共食材").isNull();
        assertThat(cap.getValue().getPurchaseCategoryId()).isEqualTo(2L);
        verify(references).merge(List.of(11L, 12L, 13L), 100L);
        verify(ingredients).removeById(11L);
        verify(ingredients).removeById(12L);
        verify(ingredients).removeById(13L);
        assertThat(r.mergedIngredients()).isEqualTo(3);
        assertThat(r.movedRows()).isEqualTo(3);
        assertThat(r.dedupedRows()).isEqualTo(1);
        assertThat(r.aliasesAdded()).as("各家写法都与目标名相同，无需别名").isEmpty();
        ArgumentCaptor<OperationLog> log = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().action()).isEqualTo(OperationAction.ADOPT_INGREDIENT);
        assertThat(log.getValue().targetId()).isEqualTo(100L);
        assertThat(log.getValue().operatorId()).isEqualTo(9L);
    }

    @Test
    void 收编_名称与已有公共名或别名冲突_拒绝() {
        givenPrivates();
        when(catalog.findPublicByNameOrAlias("青花椒油")).thenReturn(priv(1L, "花椒油", null, 2L, null));
        assertThatThrownBy(() -> svc.adopt(9L, "青花椒油", "青花椒油", 2L, null))
                .isInstanceOf(BizException.class).hasMessageContaining("合并到已有公共食材");
        verify(references, never()).merge(any(), any());
    }

    @Test
    void 合并到已有公共食材_各家不同写法记为别名() {
        givenPrivates();
        Ingredient target = priv(50L, "花椒油", null, 2L, null);
        when(ingredients.getById(50L)).thenReturn(target);
        when(catalog.findPublicByNameOrAlias(any())).thenReturn(null);
        when(references.merge(any(), any())).thenReturn(IngredientReferences.MergeStats.empty());

        AdoptResult r = svc.mergeInto(9L, "青花椒油", 50L, null);

        verify(references).merge(List.of(11L, 12L, 13L), 50L);
        ArgumentCaptor<IngredientAlias> a = ArgumentCaptor.forClass(IngredientAlias.class);
        verify(aliases, times(1)).insert(a.capture()); // 三条同名 -> 去重后只记一条别名
        assertThat(a.getValue().getAlias()).isEqualTo("青花椒油");
        assertThat(a.getValue().getIngredientId()).isEqualTo(50L);
        assertThat(r.aliasesAdded()).containsExactly("青花椒油");
        ArgumentCaptor<OperationLog> log = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().action()).isEqualTo(OperationAction.MERGE_INGREDIENT);
    }

    @Test
    void 合并_目标不是公共食材_拒绝() {
        givenPrivates();
        when(ingredients.getById(60L)).thenReturn(priv(60L, "别家私有", 5L, 1L, null));
        assertThatThrownBy(() -> svc.mergeInto(9L, "青花椒油", 60L, null)).isInstanceOf(BizException.class);
        verify(references, never()).merge(any(), any());
    }

    @Test
    void 合并_找不到同名私有食材_拒绝() {
        givenPrivates();
        assertThatThrownBy(() -> svc.mergeInto(9L, "不存在的名字", 50L, null)).isInstanceOf(BizException.class);
    }

    // ---------------------------------------------------------------- 别名维护

    @Test
    void 新增别名_规整_指向公共食材_写日志() {
        when(ingredients.getById(3L)).thenReturn(priv(3L, "马铃薯", null, 1L, null));
        when(catalog.findPublicByNameOrAlias("洋芋")).thenReturn(null);

        var item = svc.addAlias(9L, " 洋芋 ", 3L);

        assertThat(item.alias()).isEqualTo("洋芋");
        assertThat(item.ingredientName()).isEqualTo("马铃薯");
        verify(aliases).insert(any(IngredientAlias.class));
        ArgumentCaptor<OperationLog> log = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().action()).isEqualTo(OperationAction.ADD_INGREDIENT_ALIAS);
    }

    @Test
    void 新增别名_目标是私有食材_或与已有名称别名冲突_或为空_拒绝() {
        when(ingredients.getById(8L)).thenReturn(priv(8L, "家传酱", 7L, 1L, null));
        assertThatThrownBy(() -> svc.addAlias(9L, "酱", 8L)).hasMessageContaining("公共食材");

        when(ingredients.getById(3L)).thenReturn(priv(3L, "马铃薯", null, 1L, null));
        when(catalog.findPublicByNameOrAlias("番茄")).thenReturn(priv(1L, "番茄", null, 1L, null));
        assertThatThrownBy(() -> svc.addAlias(9L, "番茄", 3L)).hasMessageContaining("已是公共食材");
        assertThatThrownBy(() -> svc.addAlias(9L, "  ", 3L)).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> svc.addAlias(9L, "马铃薯", 3L)).hasMessageContaining("相同");
        verify(aliases, never()).insert(any(IngredientAlias.class));
    }

    @Test
    void 删除别名_写日志_不存在报404() {
        IngredientAlias a = new IngredientAlias();
        a.setId(5L);
        a.setAlias("洋芋");
        a.setIngredientId(3L);
        when(aliases.selectById(5L)).thenReturn(a);

        svc.deleteAlias(9L, 5L);

        verify(aliases).deleteById(5L);
        ArgumentCaptor<OperationLog> log = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().action()).isEqualTo(OperationAction.DEL_INGREDIENT_ALIAS);
        assertThatThrownBy(() -> svc.deleteAlias(9L, 6L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }
}

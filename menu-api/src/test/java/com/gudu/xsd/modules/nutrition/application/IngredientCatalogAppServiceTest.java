package com.gudu.xsd.modules.nutrition.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientAlias;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.domain.repository.IngredientReferences;
import com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/** 家庭私有食材：新建去重规则、改 / 删权限、在用拒绝删除。 */
class IngredientCatalogAppServiceTest {

    private final IngredientService ingredients = mock(IngredientService.class);
    private final IngredientAliasMapper aliases = mock(IngredientAliasMapper.class);
    private final IngredientReferences references = mock(IngredientReferences.class);
    private final CatalogWriteGuard guard = mock(CatalogWriteGuard.class);
    private final ContentSecurityAppService security = mock(ContentSecurityAppService.class);
    private IngredientCatalogAppService svc;

    @BeforeEach
    void setUp() {
        svc = new IngredientCatalogAppService(ingredients, aliases, references, guard, security);
        TenantContextHolder.set(new TenantContext(7L, 1L, 1L, TenantContext.MEMBER));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static Ingredient ing(Long id, String name, Long familyId) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setFamilyId(familyId);
        return i;
    }

    // ---------------------------------------------------------------- 新建

    @Test
    void 新建_名称与公共食材一致_返回公共id_不新建() {
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(ing(3L, "马铃薯", null));

        var r = svc.resolveOrCreate(ing(null, " 马铃薯 ", null));

        assertThat(r.id()).isEqualTo(3L);
        assertThat(r.existed()).isEqualTo("public");
        assertThat(r.familyOwned()).isFalse();
        assertThat(r.tip()).isEqualTo("已有公共食材：马铃薯");
        verify(ingredients, never()).save(any());
    }

    @Test
    void 新建_名称命中别名_返回别名指向的公共食材() {
        IngredientAlias a = new IngredientAlias();
        a.setAlias("土豆");
        a.setIngredientId(3L);
        // 第一次查公共同名 -> 无；别名命中；第二次按别名目标 id 查公共食材
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(null, ing(3L, "马铃薯", null));
        when(aliases.selectOne(any())).thenReturn(a);

        var r = svc.resolveOrCreate(ing(null, "土豆", null));

        assertThat(r.id()).isEqualTo(3L);
        assertThat(r.name()).isEqualTo("马铃薯");
        assertThat(r.existed()).isEqualTo("public");
        verify(ingredients, never()).save(any());
    }

    @Test
    void 新建_本家已有同名私有_返回已有id() {
        // 公共同名无 -> 别名无 -> 本家同名有
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(null, ing(8L, "家传酱", 7L));

        var r = svc.resolveOrCreate(ing(null, "家传酱", null));

        assertThat(r.id()).isEqualTo(8L);
        assertThat(r.existed()).isEqualTo("family");
        assertThat(r.familyOwned()).isTrue();
        verify(ingredients, never()).save(any());
    }

    @Test
    void 新建_都没有_建本家私有食材_规整名称_过内容安全_不带营养() {
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(null);
        doAnswer(inv -> {
            ((Ingredient) inv.getArgument(0)).setId(55L);
            return true;
        }).when(ingredients).save(any());
        Ingredient in = ing(99L, "　新（鲜）食材  ", null);
        in.setEdible(2);

        var r = svc.resolveOrCreate(in);

        ArgumentCaptor<Ingredient> cap = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredients).save(cap.capture());
        Ingredient saved = cap.getValue();
        assertThat(saved.getName()).isEqualTo("新(鲜)食材");
        assertThat(saved.getFamilyId()).isEqualTo(7L);
        assertThat(saved.getEdible()).isEqualTo(2);
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(r.id()).isEqualTo(55L);
        assertThat(r.existed()).isEqualTo("created");
        verify(security).assertUserText(ContentPurpose.CATALOG_NAME, "新(鲜)食材");
        verify(ingredients, never()).saveWithNutrition(any(), any());
    }

    @Test
    void 新建_空名称或违规名称被拒() {
        assertThatThrownBy(() -> svc.resolveOrCreate(ing(null, "   ", null))).isInstanceOf(BizException.class);
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(null);
        doThrow(BizException.contentBlocked("违规")).when(security).assertUserText(any(), any(String[].class));
        assertThatThrownBy(() -> svc.resolveOrCreate(ing(null, "违规词", null)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(451));
        verify(ingredients, never()).save(any());
    }

    // ---------------------------------------------------------------- 修改

    @Test
    void 修改公共食材_非后台运营被拒() {
        when(ingredients.getById(3L)).thenReturn(ing(3L, "番茄", null));
        when(guard.canMaintainPublic()).thenReturn(false);
        assertThatThrownBy(() -> svc.update(ing(3L, "改名", null)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        verify(ingredients, never()).updateById(any());
    }

    @Test
    void 修改公共食材_后台运营放行() {
        when(ingredients.getById(3L)).thenReturn(ing(3L, "番茄", null));
        when(guard.canMaintainPublic()).thenReturn(true);
        Ingredient in = ing(3L, "西红柿", null);
        svc.update(in);
        verify(ingredients).updateById(in);
    }

    @Test
    void 修改别家私有食材_读不到按不存在处理() {
        when(ingredients.getById(8L)).thenReturn(null);
        assertThatThrownBy(() -> svc.update(ing(8L, "x", null)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    void 修改本家私有食材_只改名称_分类_食用属性() {
        when(ingredients.getById(8L)).thenReturn(ing(8L, "家传酱", 7L));
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(null); // 无公共同名、无本家重名
        Ingredient in = ing(8L, "家传酱2", 123L); // 客户端传的 familyId 无效
        in.setPurchaseCategoryId(5L);
        in.setEdible(3);
        in.setUsageCount(999);

        svc.update(in);

        ArgumentCaptor<Ingredient> cap = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredients).updateById(cap.capture());
        Ingredient u = cap.getValue();
        assertThat(u.getId()).isEqualTo(8L);
        assertThat(u.getName()).isEqualTo("家传酱2");
        assertThat(u.getPurchaseCategoryId()).isEqualTo(5L);
        assertThat(u.getEdible()).isEqualTo(3);
        assertThat(u.getFamilyId()).isNull();
        assertThat(u.getUsageCount()).isNull();
        verify(security).assertUserText(ContentPurpose.CATALOG_NAME, "家传酱2");
    }

    @Test
    void 修改本家私有食材_改成公共名或别名_拒绝() {
        when(ingredients.getById(8L)).thenReturn(ing(8L, "家传酱", 7L));
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(ing(3L, "番茄", null));
        assertThatThrownBy(() -> svc.update(ing(8L, "番茄", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("已有公共食材");
        verify(ingredients, never()).updateById(any());
    }

    @Test
    void 修改本家私有食材_与本家另一条重名_拒绝() {
        when(ingredients.getById(8L)).thenReturn(ing(8L, "家传酱", 7L));
        // 公共同名无 -> 别名无 -> 本家同名(另一条)有
        when(ingredients.getOne(any(), anyBoolean())).thenReturn(null, ing(9L, "秘制", 7L));
        assertThatThrownBy(() -> svc.update(ing(8L, "秘制", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("已经有");
    }

    // ---------------------------------------------------------------- 删除

    @Test
    void 删除本家私有食材_在用则拒绝并提示() {
        when(ingredients.getById(8L)).thenReturn(ing(8L, "家传酱", 7L));
        Map<String, Long> used = new LinkedHashMap<>();
        used.put("库存", 1L);
        used.put("菜谱", 2L);
        when(references.usage(8L)).thenReturn(used);

        assertThatThrownBy(() -> svc.delete(8L))
                .isInstanceOf(BizException.class)
                .hasMessage("这个食材正在使用（库存/菜谱），不能删除");
        verify(ingredients, never()).removeById(any(java.io.Serializable.class));
    }

    @Test
    void 删除本家私有食材_未使用则软删() {
        when(ingredients.getById(8L)).thenReturn(ing(8L, "家传酱", 7L));
        when(references.usage(8L)).thenReturn(Map.of());
        svc.delete(8L);
        verify(ingredients).removeById(8L);
    }

    @Test
    void 删除公共食材_非后台运营被拒_运营放行并清别名() {
        when(ingredients.getById(3L)).thenReturn(ing(3L, "番茄", null));
        when(guard.canMaintainPublic()).thenReturn(false);
        assertThatThrownBy(() -> svc.delete(3L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        verify(ingredients, never()).removeById(any(java.io.Serializable.class));

        when(guard.canMaintainPublic()).thenReturn(true);
        svc.delete(3L);
        verify(ingredients).removeById(3L);
        verify(aliases).delete(any());
    }

    @Test
    void 删除别家私有食材_按不存在处理() {
        when(ingredients.getById(8L)).thenReturn(null);
        assertThatThrownBy(() -> svc.delete(8L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }
}

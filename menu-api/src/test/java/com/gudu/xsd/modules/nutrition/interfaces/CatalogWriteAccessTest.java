package com.gudu.xsd.modules.nutrition.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.dict.DictController;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.interfaces.OperatorGuardAspect;
import com.gudu.xsd.modules.member.AdminChecker;
import com.gudu.xsd.modules.member.AdminGuardAspect;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientController;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import com.gudu.xsd.modules.nutrition.IngredientSaveDTO;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.NutritionMetric;
import com.gudu.xsd.modules.nutrition.NutritionMetricController;
import com.gudu.xsd.modules.nutrition.NutritionMetricService;
import com.gudu.xsd.modules.nutrition.application.IngredientCatalogAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 全局共享目录写权限：运营守卫（OperatorGuardAspect 织入真实控制器）+ 普通用户受限新建逻辑。
 */
class CatalogWriteAccessTest {

    private final AccountQueryApi accounts = mock(AccountQueryApi.class);
    private final IngredientService ingSvc = mock(IngredientService.class);
    private final DictService dictSvc = mock(DictService.class);
    private final NutritionMetricService metricSvc = mock(NutritionMetricService.class);
    private final ContentSecurityAppService security = mock(ContentSecurityAppService.class);
    private final CatalogWriteGuard guard = mock(CatalogWriteGuard.class);
    private final AdminChecker adminChecker = mock(AdminChecker.class);
    private MockedStatic<StpUtil> stp;

    @BeforeEach
    void setUp() {
        stp = Mockito.mockStatic(StpUtil.class);
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
    }

    @AfterEach
    void tearDown() {
        stp.close();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxied(T target) {
        AspectJProxyFactory f = new AspectJProxyFactory(target);
        f.setProxyTargetClass(true);
        f.addAspect(new OperatorGuardAspect(accounts));
        f.addAspect(new AdminGuardAspect(adminChecker));
        return (T) f.getProxy();
    }

    private final IngredientCatalogAppService catalog = mock(IngredientCatalogAppService.class);

    private IngredientController ingredientCtl(boolean adminOperator) {
        when(guard.canMaintainPublic()).thenReturn(adminOperator);
        return new IngredientController(ingSvc, guard, catalog);
    }

    // ---------- 运营守卫：普通用户 PUT/DELETE 被拒，运营放行 ----------

    @Test
    void 普通用户改删字典_指标_全部403() {
        when(accounts.isOperator(9L)).thenReturn(false);
        DictController dict = proxied(new DictController(dictSvc, guard, security));
        NutritionMetricController metric = proxied(new NutritionMetricController(metricSvc));

        List<Runnable> calls = List.of(
                () -> dict.update(new SysDict()), () -> dict.del(1L),
                () -> metric.add(new NutritionMetric()),
                () -> metric.update(new NutritionMetric()), () -> metric.del(1L));
        for (Runnable c : calls) {
            assertThatThrownBy(c::run).isInstanceOfSatisfying(BizException.class,
                    e -> assertThat(e.getCode()).isEqualTo(403));
        }
        verifyNoInteractions(metricSvc);
        verify(dictSvc, never()).updateById(any());
        verify(dictSvc, never()).removeById(any(java.io.Serializable.class));
    }

    @Test
    void 运营改删食材交给目录用例判定_字典与指标仍仅超管() {
        when(accounts.isOperator(9L)).thenReturn(true);
        when(adminChecker.isAdmin(9L)).thenReturn(false);
        IngredientController ing = proxied(ingredientCtl(true));
        DictController dict = proxied(new DictController(dictSvc, guard, security));
        NutritionMetricController metric = proxied(new NutritionMetricController(metricSvc));

        ing.update(new Ingredient());
        ing.del(1L);
        verify(catalog).update(any(Ingredient.class));
        verify(catalog).delete(1L);
        for (Runnable c : List.<Runnable>of(() -> dict.update(new SysDict()), () -> dict.del(2L), () -> metric.del(3L))) {
            assertThatThrownBy(c::run).isInstanceOfSatisfying(BizException.class,
                    e -> assertThat(e.getCode()).isEqualTo(403));
        }
        verifyNoInteractions(metricSvc);
    }

    @Test
    void 超管可改删字典与指标() {
        when(accounts.isOperator(9L)).thenReturn(true);
        when(adminChecker.isAdmin(9L)).thenReturn(true);
        DictController dict = proxied(new DictController(dictSvc, guard, security));
        NutritionMetricController metric = proxied(new NutritionMetricController(metricSvc));

        dict.update(new SysDict());
        dict.del(2L);
        metric.del(3L);
        verify(dictSvc).updateById(any(SysDict.class));
        verify(dictSvc).removeById(2L);
        verify(metricSvc).delete(3L);
    }

    // ---------- POST /ingredient ----------

    private IngredientSaveDTO dto(Long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        IngredientSaveDTO d = new IngredientSaveDTO();
        d.setIngredient(i);
        IngredientNutrition n = new IngredientNutrition();
        n.setMetricId(1L);
        d.setNutritions(List.of(n));
        return d;
    }

    @Test
    void 普通用户新建食材_走目录用例_data为id_命中公共时msg提示() {
        IngredientController ctl = ingredientCtl(false);
        when(catalog.resolveOrCreate(any())).thenReturn(
                new IngredientCatalogAppService.Resolved(3L, "马铃薯", "public", false));

        R<?> r = ctl.add(dto(7L, "土豆"));

        assertThat(r.getData()).isEqualTo(3L);
        assertThat(r.getMsg()).isEqualTo("已有公共食材：马铃薯");
        verify(ingSvc, never()).saveWithNutrition(any(), any());
    }

    @Test
    void 运营_后台新建公共食材_规整名称_family置空_营养原样保存() {
        IngredientController ctl = ingredientCtl(true);
        IngredientSaveDTO d = dto(5L, " 运营食材 ");
        ctl.add(d);
        assertThat(d.getIngredient().getName()).isEqualTo("运营食材");
        assertThat(d.getIngredient().getId()).isNull();
        assertThat(d.getIngredient().getFamilyId()).isNull();
        verify(ingSvc).saveWithNutrition(d.getIngredient(), d.getNutritions());
        verifyNoInteractions(catalog);
    }
}

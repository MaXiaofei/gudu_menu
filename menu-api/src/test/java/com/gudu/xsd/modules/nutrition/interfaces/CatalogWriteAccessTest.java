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

    private IngredientController ingredientCtl(boolean operator) {
        when(guard.isOperator()).thenReturn(operator);
        return new IngredientController(ingSvc, guard, security);
    }

    // ---------- 运营守卫：普通用户 PUT/DELETE 被拒，运营放行 ----------

    @Test
    void 普通用户改删食材_字典_指标_全部403() {
        when(accounts.isOperator(9L)).thenReturn(false);
        IngredientController ing = proxied(ingredientCtl(false));
        DictController dict = proxied(new DictController(dictSvc, guard, security));
        NutritionMetricController metric = proxied(new NutritionMetricController(metricSvc));

        List<Runnable> calls = List.of(
                () -> ing.update(new Ingredient()), () -> ing.del(1L),
                () -> dict.update(new SysDict()), () -> dict.del(1L),
                () -> metric.add(new NutritionMetric()),
                () -> metric.update(new NutritionMetric()), () -> metric.del(1L));
        for (Runnable c : calls) {
            assertThatThrownBy(c::run).isInstanceOfSatisfying(BizException.class,
                    e -> assertThat(e.getCode()).isEqualTo(403));
        }
        verifyNoInteractions(metricSvc);
        verify(ingSvc, never()).updateById(any());
        verify(ingSvc, never()).removeById(any(java.io.Serializable.class));
        verify(dictSvc, never()).updateById(any());
        verify(dictSvc, never()).removeById(any(java.io.Serializable.class));
    }

    @Test
    void 运营可改删食材_但字典与指标仍仅超管() {
        when(accounts.isOperator(9L)).thenReturn(true);
        when(adminChecker.isAdmin(9L)).thenReturn(false);
        IngredientController ing = proxied(ingredientCtl(true));
        DictController dict = proxied(new DictController(dictSvc, guard, security));
        NutritionMetricController metric = proxied(new NutritionMetricController(metricSvc));

        ing.update(new Ingredient());
        ing.del(1L);
        verify(ingSvc).updateById(any(Ingredient.class));
        verify(ingSvc).removeById(1L);
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

    // ---------- 普通用户 POST /ingredient 收紧 ----------

    private IngredientSaveDTO dto(Long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setUsageCount(999);
        IngredientSaveDTO d = new IngredientSaveDTO();
        d.setIngredient(i);
        IngredientNutrition n = new IngredientNutrition();
        n.setMetricId(1L);
        d.setNutritions(List.of(n));
        return d;
    }

    @Test
    @SuppressWarnings("unchecked")
    void 普通用户新建食材_忽略id与营养() {
        IngredientController ctl = ingredientCtl(false);
        when(ingSvc.findByName("新食材")).thenReturn(null);
        doAnswer(inv -> {
            ((Ingredient) inv.getArgument(0)).setId(55L);
            return null;
        }).when(ingSvc).saveWithNutrition(any(), any());

        R<?> r = ctl.add(dto(7L, " 新食材 "));

        ArgumentCaptor<Ingredient> ing = ArgumentCaptor.forClass(Ingredient.class);
        ArgumentCaptor<List<IngredientNutrition>> nuts = ArgumentCaptor.forClass(List.class);
        verify(ingSvc).saveWithNutrition(ing.capture(), nuts.capture());
        assertThat(ing.getValue().getName()).isEqualTo("新食材");
        assertThat(nuts.getValue()).as("普通用户营养被忽略").isEmpty();
        assertThat(ing.getValue().getUsageCount()).isNull();
        assertThat(r.getData()).isEqualTo(55L);
        verify(security).assertUserText(ContentPurpose.CATALOG_NAME, "新食材");
    }

    @Test
    void 普通用户新建同名食材_返回已有id不新建() {
        IngredientController ctl = ingredientCtl(false);
        Ingredient exist = new Ingredient();
        exist.setId(3L);
        when(ingSvc.findByName("番茄")).thenReturn(exist);

        R<?> r = ctl.add(dto(99L, "番茄"));

        assertThat(r.getData()).isEqualTo(3L);
        verify(ingSvc, never()).saveWithNutrition(any(), any());
    }

    @Test
    void 运营新建食材_行为不变_带id与营养原样保存() {
        IngredientController ctl = ingredientCtl(true);
        IngredientSaveDTO d = dto(null, "运营食材");
        ctl.add(d);
        verify(ingSvc).saveWithNutrition(d.getIngredient(), d.getNutritions());
        verify(ingSvc, never()).findByName(any());
        verifyNoInteractions(security);
    }

    @Test
    void 普通用户新建食材_名称违规被拦截() {
        IngredientController ctl = ingredientCtl(false);
        doThrow(BizException.contentBlocked("违规")).when(security).assertUserText(any(), any(String[].class));
        assertThatThrownBy(() -> ctl.add(dto(null, "违规词")))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(451));
        verify(ingSvc, never()).saveWithNutrition(any(), any());
    }

    @Test
    void 普通用户新建食材_空名称拒绝() {
        IngredientController ctl = ingredientCtl(false);
        assertThatThrownBy(() -> ctl.add(dto(null, "  "))).isInstanceOf(BizException.class);
        verify(ingSvc, never()).saveWithNutrition(any(), any());
    }
}

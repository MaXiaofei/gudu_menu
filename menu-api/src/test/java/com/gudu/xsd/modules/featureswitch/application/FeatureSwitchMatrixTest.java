package com.gudu.xsd.modules.featureswitch.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.web.ClientSource;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService.PublicStatus;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppServiceTest.FakeRepo;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppServiceTest.MutableClock;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import com.gudu.xsd.modules.featureswitch.interfaces.FeatureSwitchController;
import com.gudu.xsd.modules.mealplan.application.MealPlanSwitchPolicy;
import com.gudu.xsd.modules.mealplan.interfaces.MealPlanController;
import com.gudu.xsd.modules.agent.interfaces.AgentController;
import com.gudu.xsd.modules.vision.application.VisionSwitchPolicy;
import com.gudu.xsd.modules.vision.interfaces.VisionController;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 四类 AI × 两端（小程序 / App）的开关矩阵：16 种组合下 publicStatus 各项相互独立，一端不影响另一端；
 * 切换立即生效；一键全关只动该端；拦截只在「生成类」入口，查看 / 历史不拦。
 */
class FeatureSwitchMatrixTest {

    FakeRepo repo;
    MutableClock clock;
    FeatureSwitchAppService svc;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        clock = new MutableClock();
        ObjectProvider<AccountQueryApi> accounts = mock(ObjectProvider.class);
        when(accounts.getIfAvailable()).thenReturn(null);
        svc = new FeatureSwitchAppService(repo, accounts, clock);
    }

    /** 掩码第 i 位为 1 表示该类别开。顺序：agentChat, cookAsk, vision, mealPlan。 */
    private static boolean bit(int mask, int i) {
        return (mask & (1 << i)) != 0;
    }

    private void apply(Platform p, int mask) {
        FeatureKey[] keys = FeatureKey.values();
        for (int i = 0; i < keys.length; i++) {
            boolean on = bit(mask, i);
            svc.set(1L, keys[i].key(p), on, on ? null : "测试关停");
        }
    }

    private static PublicStatus expected(int mask) {
        return new PublicStatus(bit(mask, 0), bit(mask, 1), bit(mask, 2), bit(mask, 3));
    }

    @Test
    void 小程序16种组合_各项相互独立_App端始终全开() {
        for (int mask = 0; mask < 16; mask++) {
            apply(Platform.MP, mask);
            clock.advance(Duration.ofSeconds(1)); // 切换已即时失效缓存，这里不依赖 TTL
            assertThat(svc.publicStatus(Platform.MP)).as("小程序 mask=%d", mask).isEqualTo(expected(mask));
            assertThat(svc.publicStatus(Platform.APP)).as("App mask=%d", mask).isEqualTo(expected(15));
        }
    }

    @Test
    void App端16种组合_各项相互独立_小程序端始终全开() {
        for (int mask = 0; mask < 16; mask++) {
            apply(Platform.APP, mask);
            assertThat(svc.publicStatus(Platform.APP)).as("App mask=%d", mask).isEqualTo(expected(mask));
            assertThat(svc.publicStatus(Platform.MP)).as("小程序 mask=%d", mask).isEqualTo(expected(15));
        }
    }

    @Test
    void 两端各16种_共256种组合互不串扰() {
        for (int mp = 0; mp < 16; mp++) {
            for (int app = 0; app < 16; app++) {
                apply(Platform.MP, mp);
                apply(Platform.APP, app);
                assertThat(svc.publicStatus(Platform.MP)).as("mp=%d app=%d", mp, app).isEqualTo(expected(mp));
                assertThat(svc.publicStatus(Platform.APP)).as("mp=%d app=%d", mp, app).isEqualTo(expected(app));
            }
        }
    }

    @Test
    void 切换后立即生效_不必等缓存过期() {
        assertThat(svc.publicStatus(Platform.MP).agentChat()).isTrue(); // 先缓存「开」
        svc.set(1L, FeatureKey.AGENT_CHAT.key(Platform.MP), false, "审核要求");
        assertThat(svc.publicStatus(Platform.MP).agentChat()).isFalse();
        assertThat(svc.publicStatus(Platform.APP).agentChat()).isTrue();
        svc.set(1L, FeatureKey.AGENT_CHAT.key(Platform.MP), true, null);
        assertThat(svc.publicStatus(Platform.MP).agentChat()).isTrue();
    }

    @Test
    void 一键全关只动该端_一键全开恢复该端() {
        svc.setAll(1L, "mp", false, "整体关停");
        assertThat(svc.publicStatus(Platform.MP)).isEqualTo(expected(0));
        assertThat(svc.publicStatus(Platform.APP)).isEqualTo(expected(15));
        svc.setAll(1L, "app", false, "App 关停");
        svc.setAll(1L, "mp", true, null);
        assertThat(svc.publicStatus(Platform.MP)).isEqualTo(expected(15));
        assertThat(svc.publicStatus(Platform.APP)).isEqualTo(expected(0));
    }

    @Test
    void 一键全关_每个类别各留一条日志_都在该端() {
        svc.setAll(1L, "mp", false, "整体关停");
        List<String> keys = new ArrayList<>();
        repo.logs.forEach(l -> keys.add(l.key()));
        assertThat(keys).hasSize(4).allMatch(k -> k.endsWith(".mp"));
    }

    @Test
    void 控制器按X_Client头判端_小程序头看mp开关_缺省与其他头看app开关() {
        apply(Platform.MP, 0b0101); // mp: agentChat 开, cookAsk 关, vision 开, mealPlan 关
        apply(Platform.APP, 0b1010);
        FeatureSwitchController c = new FeatureSwitchController(svc);
        assertThat(c.status(ClientSource.MP_WEIXIN).getData()).isEqualTo(expected(0b0101));
        assertThat(c.status(" MP-Weixin ").getData()).isEqualTo(expected(0b0101));
        assertThat(c.status(null).getData()).isEqualTo(expected(0b1010));
        assertThat(c.status("flutter").getData()).isEqualTo(expected(0b1010));
    }

    @Test
    void 公共状态接口只暴露四个布尔字段() {
        assertThat(PublicStatus.class.getRecordComponents()).extracting("name")
                .containsExactly("agentChat", "cookAsk", "vision", "mealPlan");
    }

    @Test
    void 拍照识别与一周餐程_关停时抛各自业务码_另一类与另一端不受影响() {
        VisionSwitchPolicy vision = new VisionSwitchPolicy(svc);
        MealPlanSwitchPolicy meal = new MealPlanSwitchPolicy(svc);
        svc.set(1L, FeatureKey.VISION.key(Platform.MP), false, "x");
        assertThatThrownBy(() -> vision.requireEnabled(Platform.MP)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(5034));
        vision.requireEnabled(Platform.APP);
        meal.requireEnabled(Platform.MP);
        svc.set(1L, FeatureKey.MEAL_PLAN.key(Platform.MP), false, "x");
        assertThatThrownBy(() -> meal.requireEnabled(Platform.MP)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(5035));
        meal.requireEnabled(Platform.APP);
    }

    @Test
    void 控制器_建任务入口关停时在鉴权取人之前就拒绝_不调模型() {
        svc.set(1L, FeatureKey.VISION.key(Platform.MP), false, "x");
        svc.set(1L, FeatureKey.MEAL_PLAN.key(Platform.MP), false, "x");
        VisionController vc = new VisionController(null, new VisionSwitchPolicy(svc)); // appService 为空：若放行会 NPE
        VisionController.CreateReq vr = new VisionController.CreateReq();
        vr.setImageUrl("http://x/a.png");
        assertThatThrownBy(() -> vc.create(vr, "mp-weixin")).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(5034));
        MealPlanController mc = new MealPlanController(null, new MealPlanSwitchPolicy(svc), null);
        assertThatThrownBy(() -> mc.create(null, "mp-weixin")).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(5035));
    }

    /** 只有「生成类」入口读取 X-Client 做拦截；查看结果、历史、确认 / 撤销、删除等方法不带该头参数，因此不会被拦。 */
    @Test
    void 只有生成类入口参与拦截_查看历史等方法不读端头() {
        assertThat(methodsReadingClientHeader(VisionController.class)).containsExactly("create");
        assertThat(methodsReadingClientHeader(MealPlanController.class)).containsExactly("create");
        assertThat(methodsReadingClientHeader(AgentController.class)).containsExactly("streamMessage");
        // 查看类入口仍然存在（GET），确认没有被误删
        assertThat(Arrays(VisionController.class, "get")).isTrue();
        assertThat(Arrays(MealPlanController.class, "get")).isTrue();
        assertThat(Arrays(AgentController.class, "messages")).isTrue();
        assertThat(Arrays(AgentController.class, "sessions")).isTrue();
    }

    private static boolean Arrays(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.isAnnotationPresent(GetMapping.class)) return true;
        }
        return false;
    }

    private static List<String> methodsReadingClientHeader(Class<?> c) {
        List<String> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                RequestHeader h = p.getAnnotation(RequestHeader.class);
                if (h != null && ClientSource.HEADER.equals(h.value())) out.add(m.getName());
            }
        }
        return out;
    }
}

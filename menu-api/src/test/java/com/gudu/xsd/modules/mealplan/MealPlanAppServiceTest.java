package com.gudu.xsd.modules.mealplan;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.mealplan.application.DishCardAssembler;
import com.gudu.xsd.modules.mealplan.application.DishCatalog;
import com.gudu.xsd.modules.mealplan.application.HouseholdAssembler;
import com.gudu.xsd.modules.mealplan.application.MealPlanAppService;
import com.gudu.xsd.modules.mealplan.application.dto.CreateDraftCommand;
import com.gudu.xsd.modules.mealplan.application.dto.DishCardView;
import com.gudu.xsd.modules.mealplan.application.dto.MealPlanDraftView;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.MealPlanDraft;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;
import com.gudu.xsd.modules.mealplan.domain.repository.MealPlanDraftRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import com.gudu.xsd.modules.menu.MenuSaveDTO;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.StockCoverageAppService;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.shopping.ShoppingItemVO;
import com.gudu.xsd.modules.shopping.ShoppingListVO;
import com.gudu.xsd.modules.shopping.ShoppingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealPlanAppServiceTest {

    /** 2026-10-16 周五 18:00（上海）。 */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private final Map<Long, MealPlanDraft> store = new HashMap<>();
    private final MealPlanDraftRepository repo = new MealPlanDraftRepository() {
        private long seq = 1;
        private long slotSeq = 100;

        @Override
        public void save(MealPlanDraft d) {
            if (d.id() == null) {
                List<Long> ids = d.slots().stream().map(s -> slotSeq++).toList();
                d.identify(seq++, ids);
            }
            store.put(d.id(), d);
        }

        @Override
        public Optional<MealPlanDraft> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }
    };
    private final DishCatalog catalog = mock(DishCatalog.class);
    private final StockCoverageAppService coverage = mock(StockCoverageAppService.class);
    private final MemberService memberService = mock(MemberService.class);
    private final StockAppService stock = mock(StockAppService.class);
    private final MenuService menuService = mock(MenuService.class);
    private final ShoppingService shoppingService = mock(ShoppingService.class);
    private final DictService dictService = mock(DictService.class);
    private MealPlanAppService svc;

    @BeforeEach
    void setUp() {
        Map<Long, DishCandidate> cands = new LinkedHashMap<>();
        Map<Long, DishCatalog.DishInfo> info = new LinkedHashMap<>();
        for (long i = 1; i <= 30; i++) {
            DishKind k = i % 2 == 0 ? DishKind.MEAT : DishKind.VEG;
            cands.put(i, new DishCandidate(i, "菜" + i, 20, Set.of(), Set.of(i, 1000 + i), List.of("料" + i), k,
                    new Nutrients(300, 15, 10, 300 + i * 10, 2), 900, i % 3 == 0));
            info.put(i, new DishCatalog.DishInfo(i, "菜" + i, "/c/" + i + ".jpg", 20, 2, List.of(), List.of(i, 1000 + i)));
        }
        when(catalog.load()).thenReturn(new DishCatalog.Snapshot(cands, info));
        when(coverage.coverageOf(anyMap())).thenReturn(Map.of());
        Member mama = member(11L, "妈妈", Map.of("audiences", List.of("高血压")));
        Member duo = member(12L, "朵朵", Map.of("audiences", List.of("宝宝辅食")));
        Member me = member(10L, "小王", Map.of());
        when(memberService.list()).thenReturn(List.of(me, mama, duo));
        when(memberService.listByIds(anyCollection())).thenReturn(List.of(me, mama, duo));
        when(stock.expiring(anyInt())).thenReturn(List.of(
                new ExpiringItemView(1004L, "鲈鱼", "ENOUGH", LocalDate.of(2026, 10, 17), 1),      // 周一前到期：不计
                new ExpiringItemView(1006L, "西兰花", "ENOUGH", LocalDate.of(2026, 10, 20), 4)));
        when(dictService.idsByName(eq("menu_type"), anyString())).thenReturn(Set.of(407L));
        svc = new MealPlanAppService(repo, catalog, new DishCardAssembler(coverage),
                new HouseholdAssembler(memberService), stock, menuService, shoppingService, dictService, CLOCK);
    }

    private static Member member(Long id, String name, Map<String, Object> hp) {
        Member m = new Member();
        m.setId(id);
        m.setName(name);
        m.setHealthProfile(hp);
        return m;
    }

    @Test
    void 默认下周一到周日_约束标签_快到期只算本周内的() {
        MealPlanDraftView v = svc.create(10L, null);
        assertThat(v.weekStart()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(v.weekEnd()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(v.days()).hasSize(7);
        assertThat(v.days().get(0).weekday()).isEqualTo("周一");
        assertThat(v.days().get(5).note()).isEqualTo("周末");
        assertThat(v.days()).allSatisfy(d -> assertThat(d.slots()).hasSize(2));
        assertThat(v.constraints()).contains("3 口人", "妈妈少盐", "朵朵辅食版", "优先用快到期");
        assertThat(v.check().notes()).anyMatch(n -> n.contains("≤2g"));
        // 西兰花（10.20 到期）用到了，鲈鱼（10.17，下周前就到期）不计
        assertThat(v.summary().usesExpiring()).isEqualTo(1);
        assertThat(v.days().get(0).slots()).anySatisfy(s -> {
            assertThat(s.dish().id()).isEqualTo(6L);
            assertThat(s.badges()).contains("EXPIRING");
        });
        assertThat(v.status()).isEqualTo("DRAFT");
    }

    @Test
    void 换一道_备选不重复_替换后其它格不动() {
        MealPlanDraftView v = svc.create(10L, new CreateDraftCommand(null, List.of("DINNER"), 2, null, null, null, false));
        assertThat(v.days()).allSatisfy(d -> assertThat(d.slots()).hasSize(1));
        MealPlanDraftView.Slot wed = v.days().get(2).slots().get(0);
        List<DishCardView> alts = svc.alternatives(10L, v.draftId(), wed.slotId());
        assertThat(alts).hasSizeLessThanOrEqualTo(3).isNotEmpty();
        Set<Long> inWeek = new java.util.HashSet<>();
        v.days().forEach(d -> d.slots().forEach(s -> inWeek.add(s.dish().id())));
        assertThat(alts).allSatisfy(a -> assertThat(inWeek).doesNotContain(a.id()));
        MealPlanDraftView after = svc.replace(10L, v.draftId(), wed.slotId(), alts.get(0).id());
        assertThat(after.days().get(2).slots().get(0).dish().id()).isEqualTo(alts.get(0).id());
        assertThat(after.days().get(3).slots().get(0).dish().id()).isEqualTo(v.days().get(3).slots().get(0).dish().id());
        long other = v.days().get(0).slots().get(0).dish().id();
        assertThatThrownBy(() -> svc.replace(10L, v.draftId(), wed.slotId(), other)).hasMessageContaining("已经排过");
        assertThatThrownBy(() -> svc.get(99L, v.draftId())).isInstanceOf(BizException.class);
    }

    @Test
    void 确认_按天建食集_合并采购扣掉余量已有_只能一次() {
        MealPlanDraftView v = svc.create(10L, null);
        AtomicLong menuSeq = new AtomicLong(500);
        doAnswer(inv -> {
            ((MenuSaveDTO) inv.getArgument(0)).getMenu().setId(menuSeq.incrementAndGet());
            return null;
        }).when(menuService).saveWithDishes(any());
        when(shoppingService.generate(eq("dish"), isNull(), anyList())).thenReturn(77L);
        ShoppingListVO list = new ShoppingListVO();
        list.setItems(List.of(item(1, 1001L), item(2, 1002L), item(3, 1003L)));
        when(shoppingService.getDetail(77L)).thenReturn(list);
        when(stock.levelsOf(anyCollection())).thenReturn(Map.of(1001L, StockLevel.ENOUGH, 1002L, StockLevel.NONE));

        Map<String, Object> out = svc.apply(10L, v.draftId());
        assertThat((List<?>) out.get("menuIds")).hasSize(7);
        assertThat(out.get("shoppingItemCount")).isEqualTo(2);
        verify(menuService, times(7)).saveWithDishes(any());
        verify(shoppingService).deleteItem(1L);           // 余量里有的不买
        verify(shoppingService, never()).deleteItem(2L);
        verify(shoppingService).renameList(77L, "一周餐程 10.19–10.25");
        assertThat(svc.get(10L, v.draftId()).status()).isEqualTo("APPLIED");
        assertThatThrownBy(() -> svc.apply(10L, v.draftId())).hasMessageContaining("已经生成食集");
    }

    private static ShoppingItemVO item(long id, Long ingredientId) {
        ShoppingItemVO it = new ShoppingItemVO();
        it.setId(id);
        it.setIngredientId(ingredientId);
        return it;
    }
}

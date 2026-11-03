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
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.StockCoverageAppService;
import com.gudu.xsd.modules.shopping.ShoppingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 一周餐程 x 菜谱下架（V101）：已下架的公共菜谱不进新排餐 / 换菜备选 / 换菜目标；已在草稿里的照常显示。 */
class MealPlanOfflineDishTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
    /** 30 道菜里奇数 id 全部下架，只剩偶数 id 可选。 */
    static final Set<Long> OFFLINE = new HashSet<>();

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
    private final MemberService memberService = mock(MemberService.class);
    private MealPlanAppService svc;

    @BeforeEach
    void setUp() {
        OFFLINE.clear();
        Map<Long, DishCandidate> cands = new LinkedHashMap<>();
        Map<Long, DishCatalog.DishInfo> info = new LinkedHashMap<>();
        for (long i = 1; i <= 30; i++) {
            DishKind k = i % 2 == 0 ? DishKind.MEAT : DishKind.VEG;
            cands.put(i, new DishCandidate(i, "菜" + i, 20, Set.of(), Set.of(i, 1000 + i), List.of("料" + i), k,
                    new Nutrients(300, 15, 10, 300 + i * 10, 2), 900, false));
            info.put(i, new DishCatalog.DishInfo(i, "菜" + i, "/c/" + i + ".jpg", 20, 2, List.of(), List.of(i, 1000 + i)));
            if (i % 2 == 1) OFFLINE.add(i);
        }
        when(catalog.load()).thenReturn(new DishCatalog.Snapshot(cands, info, Set.copyOf(OFFLINE)));
        StockCoverageAppService coverage = mock(StockCoverageAppService.class);
        when(coverage.coverageOf(anyMap())).thenReturn(Map.of());
        Member me = new Member();
        me.setId(10L);
        me.setName("小王");
        me.setHealthProfile(Map.of());
        when(memberService.list()).thenReturn(List.of(me));
        when(memberService.household(any())).thenReturn(List.of(me));
        when(memberService.listByIds(anyCollection())).thenReturn(List.of(me));
        StockAppService stock = mock(StockAppService.class);
        when(stock.expiring(anyInt())).thenReturn(List.of());
        DictService dictService = mock(DictService.class);
        when(dictService.idsByName(eq("menu_type"), anyString())).thenReturn(Set.of(407L));
        svc = new MealPlanAppService(repo, catalog, new DishCardAssembler(coverage),
                new HouseholdAssembler(memberService), stock, mock(MenuService.class), mock(ShoppingService.class),
                dictService, CLOCK);
    }

    @Test
    void 快照_候选池排除下架_全量候选与卡片信息仍含下架菜() {
        DishCatalog.Snapshot snap = catalog.load();
        assertThat(snap.candidateList()).extracting(DishCandidate::dishId).allMatch(id -> id % 2 == 0).hasSize(15);
        assertThat(snap.allCandidates()).hasSize(30);
        assertThat(snap.candidates()).containsKey(1L);
        assertThat(snap.info()).containsKey(1L);
        assertThat(snap.isOffline(1L)).isTrue();
        assertThat(snap.isOffline(2L)).isFalse();
        assertThat(snap.isOffline(null)).isFalse();
    }

    @Test
    void 新排餐_全是在售菜() {
        MealPlanDraftView v = svc.create(10L, new CreateDraftCommand(null, List.of("DINNER"), 1, null, null, null, false));
        assertThat(v.days()).isNotEmpty();
        v.days().forEach(d -> d.slots().forEach(s -> assertThat(OFFLINE).doesNotContain(s.dish().id())));
    }

    @Test
    void 换一道_备选不含下架_换成下架菜被拒() {
        MealPlanDraftView v = svc.create(10L, new CreateDraftCommand(null, List.of("DINNER"), 1, null, null, null, false));
        MealPlanDraftView.Slot slot = v.days().get(0).slots().get(0);
        List<DishCardView> alts = svc.alternatives(10L, v.draftId(), slot.slotId());
        assertThat(alts).isNotEmpty().allSatisfy(a -> assertThat(OFFLINE).doesNotContain(a.id()));
        assertThatThrownBy(() -> svc.replace(10L, v.draftId(), slot.slotId(), 1L))
                .isInstanceOf(BizException.class).hasMessageContaining("已下架");
    }
}

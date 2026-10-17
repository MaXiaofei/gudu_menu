package com.gudu.xsd.modules.mealplan;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.DraftSlot;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.MealPlanDraft;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;
import com.gudu.xsd.modules.mealplan.domain.model.PlanCheck;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.RecipeLine;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;
import com.gudu.xsd.modules.mealplan.domain.service.CandidateFilter;
import com.gudu.xsd.modules.mealplan.domain.service.DishProfiler;
import com.gudu.xsd.modules.mealplan.domain.service.HeuristicMealArranger;
import com.gudu.xsd.modules.mealplan.domain.service.MealPlanner;
import com.gudu.xsd.modules.mealplan.domain.service.PlanValidator;
import com.gudu.xsd.modules.mealplan.domain.service.PortionEstimator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealPlanDomainTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    static final HouseholdProfile FAMILY = new HouseholdProfile(Set.of("高血压", "宝宝辅食", "高血脂"), Set.of(), 2000);

    @Test
    void 一周从周一开始_默认下周一_当天周一则本周() {
        assertThat(WeekRange.defaultFor(LocalDate.of(2026, 10, 16)).start()).isEqualTo(MONDAY); // 周五
        assertThat(WeekRange.defaultFor(LocalDate.of(2026, 10, 18)).start()).isEqualTo(MONDAY); // 周日
        assertThat(WeekRange.defaultFor(MONDAY).start()).isEqualTo(MONDAY);
        WeekRange w = new WeekRange(MONDAY);
        assertThat(w.end()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(w.days()).hasSize(7);
        assertThat(WeekRange.weekdayLabel(w.end())).isEqualTo("周日");
        assertThatThrownBy(() -> new WeekRange(LocalDate.of(2026, 10, 17))).isInstanceOf(DomainException.class);
    }

    @Test
    void 用量估克_与菜品画像() {
        assertThat(PortionEstimator.grams(line("五花肉", "畜禽肉", "1", "斤"))).isEqualTo(500);
        assertThat(PortionEstimator.grams(line("鸡蛋", "蛋类", "3", "个"))).isEqualTo(150);
        assertThat(PortionEstimator.grams(line("盐", "调味料", null, "适量"))).isEqualTo(2);
        assertThat(PortionEstimator.grams(line("生抽", "调味料", "2", "大勺"))).isEqualTo(30);
        assertThat(PortionEstimator.grams(line("虾仁", "水产海鲜", "200", null))).isEqualTo(200); // 只有数字 ≥20 视为克
        assertThat(PortionEstimator.grams(line("番茄", "蔬菜", "2", null))).isEqualTo(300);       // 小数字视为件数

        List<RecipeLine> mapo = List.of(line("北豆腐", "豆制品", "1", "块"), line("肉末", "畜禽肉", "100", "克"),
                line("郫县豆瓣酱", "调味料", "1", "大勺"), line("盐", "调味料", null, "少许"));
        DishCandidate c = DishProfiler.candidate(1, "麻婆豆腐", 25, Set.of("中辣", "荤菜"), Set.of(1L, 2L), mapo);
        assertThat(c.kind()).isEqualTo(DishKind.MEAT);
        assertThat(c.babyFriendly()).isFalse();
        assertThat(c.perServing().sodiumMg()).isGreaterThan(300); // 豆瓣酱 + 盐
        assertThat(c.costFen()).isPositive();

        List<RecipeLine> egg = List.of(line("鸡蛋", "蛋类", "2", "个"), line("盐", "调味料", null, "少许"));
        DishCandidate steamed = DishProfiler.candidate(2, "蒸水蛋", 15, Set.of("蒸", "清淡"), Set.of(3L), egg);
        assertThat(steamed.babyFriendly()).isTrue();
        assertThat(DishProfiler.candidate(3, "冬瓜排骨汤", 60, Set.of(), Set.of(), List.of()).kind())
                .isEqualTo(DishKind.SOUP);

        // 用料没关联任何调料（导入菜谱常漏「盐 适量」）：咸口菜按家常用盐补钠；甜品不补
        List<RecipeLine> noSalt = List.of(line("五花肉", "畜禽肉", "500", "克"));
        DishCandidate pork = DishProfiler.candidate(4, "红烧肉", 60, Set.of(), Set.of(5L), noSalt);
        assertThat(pork.perServing().sodiumMg()).isGreaterThan(400);
        DishCandidate pear = DishProfiler.candidate(5, "冰糖炖梨", 40, Set.of(), Set.of(6L),
                List.of(line("雪梨", "水果", "1", "个")));
        assertThat(pear.kind()).isEqualTo(DishKind.DESSERT);
        assertThat(pear.perServing().sodiumMg()).isLessThan(10);
    }

    @Test
    void 规则过滤_高血压限钠_忌口_工作日时长() {
        DishCandidate salty = cand(1, DishKind.MEAT, 1200, false, Set.of(), 20);
        DishCandidate light = cand(2, DishKind.VEG, 200, false, Set.of(), 50);
        assertThat(CandidateFilter.allowed(salty, FAMILY)).isFalse();
        assertThat(CandidateFilter.allowed(light, FAMILY)).isTrue();
        HouseholdProfile peanut = new HouseholdProfile(Set.of(), Set.of("花生"), 2300);
        DishCandidate gongbao = new DishCandidate(3, "宫保鸡丁", 20, Set.of(), Set.of(9L), List.of("鸡胸", "花生米"),
                DishKind.MEAT, new Nutrients(300, 20, 10, 500, 1), 1000, false);
        assertThat(CandidateFilter.allowed(gongbao, peanut)).isFalse();
        assertThat(CandidateFilter.fitsTime(light, MONDAY, 30)).isFalse();             // 工作日 50 分钟 > 30
        assertThat(CandidateFilter.fitsTime(light, MONDAY.plusDays(5), 30)).isTrue();  // 周六不限
    }

    @Test
    void 排一周_每格不重样_每天有宝宝菜_每天钠达标_工作日时长() {
        List<DishCandidate> pool = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            DishKind k = i % 3 == 0 ? DishKind.SOUP : (i % 2 == 0 ? DishKind.MEAT : DishKind.VEG);
            pool.add(cand(i, k, 300 + i * 10, i % 2 == 1, Set.of((long) i), i % 5 == 0 ? 60 : 25));
        }
        PlanRequest req = new PlanRequest(new WeekRange(MONDAY), null, 4, List.of(), 30, null, true);
        MealPlanner.Outcome out = new MealPlanner(new HeuristicMealArranger()).plan(req, FAMILY, pool, Set.of(8L, 12L));
        assertThat(out.slots()).hasSize(14);
        assertThat(out.slots().stream().map(PlannedSlot::dishId).distinct()).hasSize(14);
        Map<Long, DishCandidate> byId = pool.stream().collect(Collectors.toMap(DishCandidate::dishId, c -> c));
        Map<LocalDate, List<PlannedSlot>> byDay = PlanValidator.groupByDay(out.slots());
        assertThat(byDay).hasSize(7);
        byDay.forEach((d, ds) -> {
            assertThat(ds).anyMatch(s -> s.badges().contains(SlotBadge.BABY));
            assertThat(PlanValidator.dailySodium(ds, byId)).isLessThanOrEqualTo(2000);
            if (WeekRange.isWeekday(d)) ds.forEach(s -> assertThat(byId.get(s.dishId()).minutes()).isLessThanOrEqualTo(30));
        });
        // 快到期优先：用到 8、12 号食材的菜排进来且带角标
        assertThat(out.slots()).anyMatch(s -> s.dishId() == 8L && s.badges().contains(SlotBadge.EXPIRING));
        assertThat(out.check().passed()).isTrue();
        assertThat(out.check().rounds()).isEqualTo(1);
        assertThat(out.check().notes()).anyMatch(n -> n.startsWith("钠日均")).contains("14 道不重样");
    }

    @Test
    void 少盐约束_超标的天回炉_第二轮通过() {
        // 单菜都在 900mg 以内（过了规则过滤），但两道 850 + 基线 300 = 2000 > 1900 上限
        HouseholdProfile strict = new HouseholdProfile(Set.of("高血压"), Set.of(), 1900);
        List<DishCandidate> pool = new ArrayList<>();
        pool.add(cand(1, DishKind.MEAT, 850, false, Set.of(100L), 20)); // 用到快到期食材，周一会被优先选
        pool.add(cand(2, DishKind.VEG, 850, false, Set.of(101L), 20));
        for (int i = 3; i <= 20; i++) pool.add(cand(i, i % 2 == 0 ? DishKind.MEAT : DishKind.VEG, 300, false, Set.of(), 20));
        PlanRequest req = new PlanRequest(new WeekRange(MONDAY), null, 3, List.of(), null, null, true);
        MealPlanner.Outcome out = new MealPlanner(new HeuristicMealArranger()).plan(req, strict, pool, Set.of(100L, 101L));
        assertThat(out.check().rounds()).isEqualTo(2);
        assertThat(out.check().passed()).isTrue();
        Map<Long, DishCandidate> byId = pool.stream().collect(Collectors.toMap(DishCandidate::dishId, c -> c));
        PlanValidator.groupByDay(out.slots()).values()
                .forEach(ds -> assertThat(PlanValidator.dailySodium(ds, byId)).isLessThanOrEqualTo(1900));
    }

    @Test
    void 回炉两轮仍不通过时标注提醒() {
        HouseholdProfile strict = new HouseholdProfile(Set.of(), Set.of(), 500); // 低到不可能
        List<DishCandidate> pool = new ArrayList<>();
        for (int i = 1; i <= 40; i++) pool.add(cand(i, DishKind.VEG, 400, false, Set.of(), 20));
        PlanRequest req = new PlanRequest(new WeekRange(MONDAY), List.of(Meal.DINNER), 2, List.of(), null, null, false);
        MealPlanner.Outcome out = new MealPlanner(new HeuristicMealArranger()).plan(req, strict, pool, Set.of());
        assertThat(out.check().passed()).isFalse();
        assertThat(out.check().rounds()).isEqualTo(3);
        assertThat(out.check().notes()).anyMatch(n -> n.contains("超出"));
    }

    @Test
    void 草稿换一道_本周不重复_确认后不可再改() {
        PlanRequest req = new PlanRequest(new WeekRange(MONDAY), List.of(Meal.DINNER), 2, List.of(), null, null, false);
        List<PlannedSlot> planned = new ArrayList<>();
        for (int i = 0; i < 7; i++) planned.add(new PlannedSlot(MONDAY.plusDays(i), Meal.DINNER, i + 1, Set.of()));
        MealPlanDraft d = MealPlanDraft.generate(9L, req, List.of("2 口人"), planned, new PlanCheck(true, 1, List.of()),
                LocalDateTime.now());
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 7; i++) ids.add(100 + i);
        d.identify(1L, ids);
        DraftSlot wed = d.slots().get(2);
        assertThatThrownBy(() -> d.replaceDish(wed.slotId(), 1, Set.of())).hasMessageContaining("已经排过");
        d.replaceDish(wed.slotId(), 50, EnumSet.of(SlotBadge.BABY));
        assertThat(d.slot(wed.slotId()).dishId()).isEqualTo(50);
        assertThat(d.slot(wed.slotId()).badges()).containsExactly(SlotBadge.BABY);
        assertThat(d.slots().get(3).dishId()).isEqualTo(4); // 其它格不动
        d.markApplied(List.of(11L));
        assertThatThrownBy(() -> d.replaceDish(wed.slotId(), 60, Set.of())).hasMessageContaining("已经生成食集");
        assertThatThrownBy(() -> MealPlanDraft.generate(9L, req, List.of(),
                List.of(new PlannedSlot(MONDAY.plusDays(9), Meal.DINNER, 1, Set.of())), null, LocalDateTime.now()))
                .hasMessageContaining("超出本周");
    }

    static DishCandidate cand(long id, DishKind kind, double sodium, boolean baby, Set<Long> ings, int minutes) {
        return new DishCandidate(id, "菜" + id, minutes, Set.of(), ings, List.of(), kind,
                new Nutrients(300, 15, 10, sodium, 2), 800, baby);
    }

    static RecipeLine line(String name, String category, String amount, String unit) {
        return new RecipeLine(name, category, amount == null ? null : new BigDecimal(amount), unit, null, null, null);
    }
}

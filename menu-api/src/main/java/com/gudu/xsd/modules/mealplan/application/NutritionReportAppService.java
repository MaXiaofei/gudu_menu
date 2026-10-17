package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.domain.model.CookedDishEntry;
import com.gudu.xsd.modules.mealplan.application.dto.DishCardView;
import com.gudu.xsd.modules.mealplan.application.dto.NutritionReportView;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.MemberHealth;
import com.gudu.xsd.modules.mealplan.domain.model.NutrientKey;
import com.gudu.xsd.modules.mealplan.domain.service.NutritionReportCalculator;
import com.gudu.xsd.modules.mealplan.domain.service.NutritionTargets;
import com.gudu.xsd.modules.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营养报告（GET /nutrition/report）：按做菜记录（食记）聚合本周（周一至今）或本月（1 号至今），
 * 每条按 1 人份 × 份数系数折算到该成员，对照其档案阈值。
 * multi-tenant-family 前没有「谁吃了」数据：家庭共同用餐，全部记录都计入每位成员。
 */
@Service
@RequiredArgsConstructor
public class NutritionReportAppService {

    public static final String RANGE_WEEK = "week";
    public static final String RANGE_MONTH = "month";

    private final CookingRecordAppService cookingRecordAppService;
    private final HouseholdAssembler household;
    private final DishCatalog catalog;
    private final DishCardAssembler cardAssembler;
    private final Clock clock;

    public NutritionReportView report(Long memberId, String range) {
        String r = range == null || range.isBlank() ? RANGE_WEEK : range.trim().toLowerCase();
        if (!RANGE_WEEK.equals(r) && !RANGE_MONTH.equals(r)) throw new BizException("range 只支持 week / month");
        Member member = household.member(memberId);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = RANGE_WEEK.equals(r) ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : today.withDayOfMonth(1);

        DishCatalog.Snapshot snap = catalog.load();
        List<NutritionReportCalculator.Intake> intakes = new ArrayList<>();
        for (CookedDishEntry e : cookingRecordAppService.cookedBetween(from.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            DishCandidate c = snap.candidates().get(e.dishId());
            if (c == null) continue; // 菜已删
            double factor = e.servingFactor() == null || e.servingFactor().signum() <= 0 ? 1 : e.servingFactor().doubleValue();
            intakes.add(new NutritionReportCalculator.Intake(e.cookedAt().toLocalDate(), c.dishId(),
                    c.perServing().scale(factor)));
        }
        MemberHealth health = household.health(member);
        Map<NutrientKey, Double> targets = NutritionTargets.of(health);
        NutritionReportCalculator.Result res = NutritionReportCalculator.compute(intakes, targets, health.asProfile(),
                snap.candidateList());

        Set<Long> cardIds = new LinkedHashSet<>();
        res.alerts().forEach(a -> {
            cardIds.addAll(a.sourceDishIds());
            cardIds.addAll(a.suggestionDishIds());
        });
        Map<Long, DishCardView> cards = cardAssembler.cards(cardIds, snap);
        List<NutritionReportView.RadarItem> radar = res.radar().stream()
                .map(x -> new NutritionReportView.RadarItem(x.key().key(), x.key().label(), x.actual(), x.target(),
                        x.key().unit(), x.ratio())).toList();
        List<NutritionReportView.Alert> alerts = res.alerts().stream()
                .map(a -> new NutritionReportView.Alert(a.key().key(), a.key().label(), a.dailyAvg(), a.limit(),
                        a.key().unit(), a.sourceDishIds().stream().map(cards::get).filter(java.util.Objects::nonNull).toList(),
                        a.suggestionDishIds().stream().map(cards::get).filter(java.util.Objects::nonNull).toList()))
                .toList();
        NutritionReportView.Trend trend = new NutritionReportView.Trend(NutrientKey.SODIUM.key(),
                res.sodiumTrend().stream().map(p -> new NutritionReportView.Point(p.date(), p.value())).toList(),
                res.sodiumLimit());
        return new NutritionReportView(
                new NutritionReportView.MemberView(member.getId(), member.getName(), member.getAvatarUrl(),
                        household.profileLabel(member)),
                new NutritionReportView.Range(from, today), res.recordCount(), radar, alerts, trend, res.goods());
    }
}

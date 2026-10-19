package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.modules.mealplan.application.dto.DishCardView;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.pantry.application.StockCoverageAppService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** dishId → 契约 DishCard（覆盖率按当前余量实时计算，一次批量）。 */
@Component
@RequiredArgsConstructor
public class DishCardAssembler {

    private final StockCoverageAppService coverageAppService;

    public Map<Long, DishCardView> cards(Collection<Long> dishIds, DishCatalog.Snapshot snap) {
        Map<Long, List<Long>> ingByDish = new LinkedHashMap<>();
        for (Long id : dishIds.stream().filter(Objects::nonNull).distinct().toList()) {
            DishCatalog.DishInfo info = snap.info().get(id);
            if (info != null) ingByDish.put(id, info.ingredientIds());
        }
        Map<Long, CoverageView> coverage = coverageAppService.coverageOf(ingByDish);
        Map<Long, DishCardView> out = new LinkedHashMap<>();
        for (Long id : ingByDish.keySet()) {
            DishCatalog.DishInfo info = snap.info().get(id);
            CoverageView c = coverage.get(id);
            DishCandidate cand = snap.candidates().get(id);
            out.put(id, new DishCardView(id, info.name(), info.coverUrl(), info.minutes(),
                    DishCardView.difficultyLabel(info.difficulty()),
                    c == null ? new DishCardView.Coverage(0, 0, List.of())
                            : new DishCardView.Coverage(c.enough(), c.total(),
                            c.missing().stream().map(CoverageView.Missing::name).toList()),
                    info.tags(), cand == null ? null : (int) Math.round(cand.perServing().sodiumMg())));
        }
        return out;
    }

    public List<DishCardView> list(List<Long> dishIds, DishCatalog.Snapshot snap) {
        Map<Long, DishCardView> m = cards(dishIds, snap);
        return dishIds.stream().map(m::get).filter(Objects::nonNull).toList();
    }
}

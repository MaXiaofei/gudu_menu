package com.gudu.xsd.modules.pantry.application.dto;

import com.gudu.xsd.modules.pantry.domain.model.Coverage;

import java.util.List;

/** 覆盖率出参（契约 Coverage）：{enough, total, missing[{ingredientId, name}]}。 */
public record CoverageView(int enough, int total, List<Missing> missing) {

    public record Missing(Long ingredientId, String name) {
    }

    public static CoverageView from(Coverage c) {
        return new CoverageView(c.enough(), c.total(),
                c.missing().stream().map(m -> new Missing(m.ingredientId(), m.name())).toList());
    }

    /** 排序用：enough/total（total=0 记 0）。 */
    public double ratio() {
        return total == 0 ? 0d : (double) enough / total;
    }
}

package com.gudu.xsd.modules.pantry.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.util.List;

/**
 * 余量覆盖率（值对象，不可变）：非调料用料 total 样中，家里够/低的 enough 样，缺的列在 missing。
 */
public record Coverage(int enough, int total, List<MissingIngredient> missing) {

    public Coverage {
        if (enough < 0 || total < 0 || enough > total) throw new DomainException("覆盖率数据不合法");
        missing = missing == null ? List.of() : List.copyOf(missing);
    }

    public static Coverage empty() {
        return new Coverage(0, 0, List.of());
    }

    /** enough/total；没有可计用料（total=0）视为 0（排序沉底）。 */
    public double ratio() {
        return total == 0 ? 0d : (double) enough / total;
    }

    /** 家里全够。 */
    public boolean isComplete() {
        return total > 0 && enough == total;
    }
}

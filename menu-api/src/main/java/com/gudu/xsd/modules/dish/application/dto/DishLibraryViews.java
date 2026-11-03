package com.gudu.xsd.modules.dish.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 运营「公共菜谱库」视图。 */
public final class DishLibraryViews {

    private DishLibraryViews() {
    }

    /** 公共菜谱库一行：基础信息 + 上架状态（offline=true 时带下架时间 / 原因）。 */
    public record Row(Long id, String name, String coverUrl, Integer prepTime, Integer cookTime, String source,
                      String sourceName, LocalDateTime createTime, boolean offline, LocalDateTime offlineAt,
                      String offlineReason) {
    }

    public record PageResult(List<Row> records, long total) {
    }
}

package com.gudu.xsd.modules.pantry.application.dto;

import java.util.List;

/**
 * GET /pantry/summary（prototype-ui-rollout 0.7）：余量顶部四格 + 分类 Tab 计数。
 * byCategory 按数量降序；未分类 categoryId=null、name="其他"。
 */
public record PantrySummaryView(int enough, int low, int out, int expiring3d, List<CategoryCount> byCategory) {

    public record CategoryCount(Long categoryId, String name, int count) {
    }
}

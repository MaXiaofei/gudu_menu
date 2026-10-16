package com.gudu.xsd.modules.pantry.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 余量保质期（G2/G6）：临期查询（三档模型）+ 录入时默认保质天数建议。 */
@RestController
@RequestMapping("/pantry")
@RequiredArgsConstructor
@Tag(name = "食材库存")
public class StockShelfController {

    private final StockAppService appService;

    /** days 天内到期（含已过期），按剩余天数升序。 */
    @GetMapping("/expiring")
    public R<List<ExpiringItemView>> expiring(@RequestParam(defaultValue = "3") int days) {
        return R.ok(appService.expiring(days));
    }

    /** 默认保质天数建议 {suggestedDays: number|null}（前端预填 bestBefore=今天+suggestedDays）。 */
    @GetMapping("/shelf-life")
    public R<Map<String, Integer>> shelfLife(@RequestParam Long ingredientId) {
        Map<String, Integer> out = new HashMap<>();
        out.put("suggestedDays", appService.suggestShelfLifeDays(ingredientId));
        return R.ok(out);
    }
}

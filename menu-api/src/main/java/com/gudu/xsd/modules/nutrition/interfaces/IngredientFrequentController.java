package com.gudu.xsd.modules.nutrition.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.nutrition.application.IngredientFrequencyAppService;
import com.gudu.xsd.modules.nutrition.application.IngredientFrequencyAppService.FrequentIngredient;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 常用食材（G11，录菜谱「常用食材」快捷选）。 */
@RestController
@RequestMapping("/ingredient")
@RequiredArgsConstructor
@Tag(name = "食材库")
public class IngredientFrequentController {

    private final IngredientFrequencyAppService appService;

    @GetMapping("/frequent")
    public R<List<FrequentIngredient>> frequent(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(appService.frequent(StpUtil.getLoginIdAsLong(), limit));
    }
}

package com.gudu.xsd.modules.dailylog;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 每日饮食记录接口。范式照 mealplan/dish：返回 R<T>，@Tag 分组。
 * - POST / 提交当天日志（session memberId）
 * - GET /?date= 查当天日志(含 items)
 * - GET /{logId}/nutrition 总营养(metricId → value)
 */
@RestController
@RequestMapping("/dailylog")
@RequiredArgsConstructor
@Tag(name = "每日饮食记录")
public class DailyLogController {

    private final DailyLogService svc;

    /** 提交当天日志。返回 logId。 */
    @PostMapping
    public R<Long> submit(@Valid @RequestBody DailyLogSaveDTO dto) {
        return R.ok(svc.submit(dto));
    }

    /** 查当天日志（含 items）。无则 data=null。 */
    @GetMapping
    public R<DailyLogVO> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(svc.listByDate(svc.currentMemberId(), date));
    }

    /** 总营养汇总：metricId → value。 */
    @GetMapping("/{logId}/nutrition")
    public R<Map<Long, BigDecimal>> nutrition(@PathVariable Long logId) {
        return R.ok(svc.nutritionSummary(logId));
    }
}

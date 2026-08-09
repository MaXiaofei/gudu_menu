package com.gudu.xsd.modules.foodlog;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 食记（做菜日记）接口：统计卡 + 时间轴 + 按菜汇总 + 年视图 + 单条详情。
 * 数据源 cooking_record（做菜确认自动写入），不做任何手工录入。
 */
@RestController
@RequestMapping("/food-log")
@RequiredArgsConstructor
@Tag(name = "食记")
public class FoodLogController {

    private final FoodLogService svc;

    /** 月视图：统计卡 + 时间轴（一顿饭一条）。筛选：meal 餐次 / source 做菜方式 / reviewed 评价状态。 */
    @GetMapping("/month")
    public R<FoodLogService.MonthVO> month(@RequestParam String month,
                                           @RequestParam(required = false) String meal,
                                           @RequestParam(required = false) String source,
                                           @RequestParam(required = false) Boolean reviewed) {
        int[] ym = parseMonth(month);
        return R.ok(svc.month(currentMemberId(), ym[0], ym[1], meal, source, reviewed));
    }

    /** 按菜汇总：次数降序，每项带最近时间 + ★综合均分（未评价不显示）。 */
    @GetMapping("/by-dish")
    public R<FoodLogService.ByDishVO> byDish(@RequestParam String month,
                                             @RequestParam(required = false) String meal,
                                             @RequestParam(required = false) String source,
                                             @RequestParam(required = false) Boolean reviewed) {
        int[] ym = parseMonth(month);
        return R.ok(svc.byDish(currentMemberId(), ym[0], ym[1], meal, source, reviewed));
    }

    /** 年视图：12 个月做饭次数（0 = 无记录）。 */
    @GetMapping("/year")
    public R<FoodLogService.YearVO> year(@RequestParam int year) {
        return R.ok(svc.year(currentMemberId(), year));
    }

    /** 单条详情：菜列表（份数/备注）+ 用材（用完/用了一些）+ 已评状态。 */
    @GetMapping("/detail")
    public R<FoodLogService.DetailVO> detail(@RequestParam Long menuId) {
        return R.ok(svc.detail(currentMemberId(), menuId));
    }

    /** month=YYYY 或 YYYY-MM → [year, month]（month=0 表示全年范围，年视图用）。 */
    private int[] parseMonth(String month) {
        try {
            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (m < 0 || m > 12) throw new NumberFormatException();
            return new int[]{year, m};
        } catch (Exception e) {
            throw new BizException("月份格式应为 YYYY 或 YYYY-MM");
        }
    }

    private Long currentMemberId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}

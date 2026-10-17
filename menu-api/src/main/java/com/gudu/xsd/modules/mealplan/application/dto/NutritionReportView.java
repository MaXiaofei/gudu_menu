package com.gudu.xsd.modules.mealplan.application.dto;

import java.time.LocalDate;
import java.util.List;

/** 契约 NutritionReport。 */
public record NutritionReportView(MemberView member, Range range, int recordCount, List<RadarItem> radar,
                                  List<Alert> alerts, Trend trend, List<String> goods) {

    /** MemberLite & { profile }。 */
    public record MemberView(Long memberId, String name, String avatarUrl, String profile) {
    }

    public record Range(LocalDate from, LocalDate to) {
    }

    public record RadarItem(String key, String label, double actual, double target, String unit, double ratio) {
    }

    public record Alert(String key, String label, double dailyAvg, double limit, String unit,
                        List<DishCardView> sources, List<DishCardView> suggestions) {
    }

    public record Trend(String key, List<Point> points, double limit) {
    }

    public record Point(LocalDate date, double value) {
    }
}

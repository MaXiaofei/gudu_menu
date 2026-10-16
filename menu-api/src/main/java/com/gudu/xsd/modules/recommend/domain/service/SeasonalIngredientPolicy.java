package com.gudu.xsd.modules.recommend.domain.service;

import java.time.Month;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 季节鲜（纯函数）：月份 → 当季食材名（华北/华东家常时令，食材库按名匹配，匹配不到的忽略）。
 */
public final class SeasonalIngredientPolicy {

    private static final Map<Month, List<String>> TABLE = new EnumMap<>(Month.class);

    static {
        TABLE.put(Month.JANUARY, List.of("白菜", "萝卜", "菠菜", "冬笋", "山药", "莲藕"));
        TABLE.put(Month.FEBRUARY, List.of("菠菜", "韭菜", "春笋", "白菜", "萝卜", "荠菜"));
        TABLE.put(Month.MARCH, List.of("春笋", "韭菜", "荠菜", "菠菜", "豌豆苗", "香椿"));
        TABLE.put(Month.APRIL, List.of("春笋", "香椿", "蚕豆", "芦笋", "韭菜", "莴笋"));
        TABLE.put(Month.MAY, List.of("蚕豆", "豌豆", "芦笋", "黄瓜", "西葫芦", "樱桃"));
        TABLE.put(Month.JUNE, List.of("黄瓜", "番茄", "茄子", "豇豆", "苦瓜", "西瓜"));
        TABLE.put(Month.JULY, List.of("番茄", "茄子", "冬瓜", "丝瓜", "苦瓜", "毛豆"));
        TABLE.put(Month.AUGUST, List.of("冬瓜", "丝瓜", "毛豆", "玉米", "茄子", "辣椒"));
        TABLE.put(Month.SEPTEMBER, List.of("玉米", "南瓜", "莲藕", "山药", "螃蟹", "板栗"));
        TABLE.put(Month.OCTOBER, List.of("南瓜", "莲藕", "山药", "板栗", "螃蟹", "芋头"));
        TABLE.put(Month.NOVEMBER, List.of("白菜", "萝卜", "山药", "莲藕", "芋头", "菠菜"));
        TABLE.put(Month.DECEMBER, List.of("白菜", "萝卜", "冬笋", "菠菜", "山药", "羊肉"));
    }

    public List<String> ingredientNamesFor(Month month) {
        return month == null ? List.of() : TABLE.getOrDefault(month, List.of());
    }
}

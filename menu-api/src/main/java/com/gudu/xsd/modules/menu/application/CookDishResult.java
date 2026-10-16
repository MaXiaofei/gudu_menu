package com.gudu.xsd.modules.menu.application;

/** 单菜直做结果（POST /cook/dish/{dishId}）。 */
public record CookDishResult(Long recordId, Long dishId) {
}

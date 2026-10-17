package com.gudu.xsd.modules.dish.application.dto;

/** 做菜人（菜谱列表「妈妈做的」）：成员 id + 名字 + 头像（可空）。 */
public record CookerView(Long memberId, String name, String avatarUrl) {
}

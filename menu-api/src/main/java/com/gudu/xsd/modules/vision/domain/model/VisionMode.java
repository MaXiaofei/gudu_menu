package com.gudu.xsd.modules.vision.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 拍照识别模式：冰箱/台面食材、购物小票、菜谱截图。 */
public enum VisionMode {
    FRIDGE, RECEIPT, RECIPE;

    public static VisionMode of(String value) {
        if (value != null) {
            for (VisionMode m : values()) {
                if (m.name().equalsIgnoreCase(value.trim())) return m;
            }
        }
        throw new DomainException("识别模式不合法（FRIDGE / RECEIPT / RECIPE）");
    }
}

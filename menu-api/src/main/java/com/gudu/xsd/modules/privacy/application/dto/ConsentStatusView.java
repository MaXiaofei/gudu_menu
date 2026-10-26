package com.gudu.xsd.modules.privacy.application.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** 单独同意状态：docType（sensitive_health / child_info）→ 当前是否同意、版本、时间。 */
public record ConsentStatusView(Map<String, Item> items) {

    public record Item(boolean granted, String version, LocalDateTime at) {
    }
}

package com.gudu.xsd.modules.consent.application.dto;

import java.time.LocalDateTime;

/** 同意记录视图（后台查询 / 本人查看）。clientIp 只给后台。 */
public record ConsentView(Long id, Long accountId, String docType, String docVersion, boolean granted,
                          String channel, String clientIp, LocalDateTime createdAt) {
}

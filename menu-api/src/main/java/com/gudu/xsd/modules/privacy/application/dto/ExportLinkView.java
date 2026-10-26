package com.gudu.xsd.modules.privacy.application.dto;

import java.time.LocalDateTime;

/** 个人数据临时下载链接：path 为相对 API 根（如 /gudu）的地址，前端拼接 BASE 后供用户复制。 */
public record ExportLinkView(String path, LocalDateTime expiresAt) {
}

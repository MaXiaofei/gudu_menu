package com.gudu.xsd.modules.contentsecurity.domain.port;

import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;

/**
 * 内容安全端口（A.6）。两个实现：
 * <ul>
 *   <li>微信实现（infrastructure.WxContentSecurity）：msgSecCheck v2 同步检文本、mediaCheckAsync v2 异步检图片；</li>
 *   <li>规则实现（domain.service.RuleContentSecurity）：共享禁发词表兜底，不支持图片。</li>
 * </ul>
 * 路由与失败策略（fail-closed / 降级）在应用层 ContentSecurityAppService。
 */
public interface ContentSecurity {

    /**
     * 同步检测文本。
     *
     * @throws ContentSecurityUnavailableException 检测服务不可用（网络、凭证、频率限制、无 openid 等）
     */
    TextVerdict checkText(TextCheck check);

    /**
     * 异步检测图片：提交成功返回 trace_id，结果经消息推送回调。
     *
     * @throws ContentSecurityUnavailableException 检测服务不可用或该实现不支持图片
     */
    String submitMedia(MediaCheckRequest request);
}

package com.gudu.xsd.modules.contentsecurity.domain.service;

import com.gudu.xsd.common.domain.SensitiveWords;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurityUnavailableException;

/**
 * 规则兜底实现（纯 JDK）：共享禁发词表 {@link SensitiveWords}（与厨房圈 RulePostModerator 同一份）。
 * 不支持图片（规则无法看图）。厨房圈帖子另有 LlmPostModerator 做风险分级进人工复核队列，不在本端口内重复实现。
 */
public final class RuleContentSecurity implements ContentSecurity {

    @Override
    public TextVerdict checkText(TextCheck check) {
        return SensitiveWords.firstHit(check.content())
                .map(TextVerdict::ruleHit)
                .orElseGet(() -> TextVerdict.pass(TextVerdict.Source.RULE));
    }

    @Override
    public String submitMedia(MediaCheckRequest request) {
        throw new ContentSecurityUnavailableException("规则实现不支持图片检测");
    }
}

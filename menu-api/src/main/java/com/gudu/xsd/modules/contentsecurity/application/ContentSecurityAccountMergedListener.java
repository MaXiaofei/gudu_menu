package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.domain.repository.TextViolationRepository;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并：内容安全的图片检测记录（content_media_check.member_id = 上传账号）与文本违规记录
 * （content_text_violation.account_id）按账号归属，W 并入 P 后同事务改指 P——
 * 否则 P 注销时找不到 W 当年上传的头像文件，后台按账号筛选 / 累计违规也会漏掉 W 名下的记录。幂等。
 */
@Component
@RequiredArgsConstructor
public class ContentSecurityAccountMergedListener {

    private final MediaCheckRepository mediaChecks;
    private final TextViolationRepository textViolations;

    @EventListener
    public void on(AccountMerged e) {
        mediaChecks.reassignAccount(e.sourceAccountId(), e.targetAccountId());
        textViolations.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}

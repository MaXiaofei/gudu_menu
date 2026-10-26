package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextViolation;
import com.gudu.xsd.modules.contentsecurity.domain.repository.TextViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 文本违规留痕（F.3）：独立事务（REQUIRES_NEW）——被拦截的请求随后抛 451 会回滚调用方事务，违规记录不能跟着回滚。
 * 留痕失败只记日志，不影响拦截本身。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextViolationRecorder {

    private final TextViolationRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long accountId, ContentPurpose purpose, TextVerdict verdict, String text) {
        try {
            repository.save(TextViolation.of(accountId, purpose, verdict, text, LocalDateTime.now(clock)));
        } catch (RuntimeException e) {
            log.warn("文本违规留痕失败 purpose={} account={}: {}", purpose, accountId, e.getMessage());
        }
    }
}

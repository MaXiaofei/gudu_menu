package com.gudu.xsd.modules.feedback.application;

import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 意见反馈「处理完成后 1 年」删除（隐私政策 §六.2）：只删已处理（DONE）且最后更新时间（≈ 处理时间）早于期限的；
 * 未处理的反馈不删。
 */
@Component
@RequiredArgsConstructor
public class FeedbackRetentionPurger implements RetentionPurger {

    private final FeedbackRepository repository;

    @Override
    public String policy() {
        return "feedback";
    }

    @Override
    public int defaultDays() {
        return 365;
    }

    @Override
    public int purgeBatch(LocalDateTime cutoff, int limit) {
        return repository.deleteDoneBefore(cutoff, limit);
    }
}

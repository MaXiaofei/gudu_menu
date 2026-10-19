package com.gudu.xsd.modules.feedback.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 意见反馈（G14）：用户提交（每账号每日 ≤ 10 条）+ 超管列表/改状态。 */
@Service
@RequiredArgsConstructor
public class FeedbackAppService {

    public static final int DAILY_LIMIT = 10;
    static final int MAX_PAGE_SIZE = 100;

    private final FeedbackRepository repository;
    private final MemberService memberService;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long memberId, String content, String contact, List<String> images, String clientInfo) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (repository.countByMemberSince(memberId, LocalDate.now(clock).atStartOfDay()) >= DAILY_LIMIT) {
            throw BizException.tooManyRequests("今天反馈次数已达上限，明天再来吧");
        }
        Feedback f = Feedback.submit(memberId, content, contact, images, clientInfo, now);
        repository.save(f);
        return f.id();
    }

    public IPage<FeedbackView> page(String status, Integer pageNum, Integer pageSize) {
        FeedbackStatus st = status == null || status.isBlank() ? null : FeedbackStatus.of(status);
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        FeedbackPage p = repository.page(st, pn, ps);
        List<Long> memberIds = p.records().stream().map(Feedback::memberId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = memberIds.isEmpty() ? Map.of() : memberService.listByIds(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, m -> m.getName() == null ? "" : m.getName(), (a, b) -> a));
        Page<FeedbackView> out = new Page<>(pn, ps);
        out.setTotal(p.total());
        out.setRecords(p.records().stream().map(f -> new FeedbackView(f.id(), f.memberId(), names.get(f.memberId()),
                f.content(), f.contact(), f.images(), f.clientInfo(), f.status().name(), f.createTime())).toList());
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        Feedback f = repository.findById(id).orElseThrow(() -> new BizException("反馈不存在"));
        f.changeStatus(FeedbackStatus.of(status));
        repository.save(f);
    }
}

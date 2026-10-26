package com.gudu.xsd.modules.feedback.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackType;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import com.gudu.xsd.modules.notification.NotificationPayload;
import com.gudu.xsd.modules.notification.NotificationService;
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

/**
 * 意见反馈（G14 + A.11 闭环）：用户提交（每账号每日 ≤ 10 条，带类型与来源引用）、查看自己的反馈与回复；
 * 超管按类型/状态筛选、回复（回复即给提交人发站内消息，点开进反馈详情）、改状态。
 */
@Service
@RequiredArgsConstructor
public class FeedbackAppService {

    public static final int DAILY_LIMIT = 10;
    static final int MAX_PAGE_SIZE = 100;
    /** 站内消息类型：反馈有回复（小程序「消息与提醒」点开 → 反馈详情）。 */
    public static final String NOTICE_TYPE_REPLY = "feedback_reply";
    static final int NOTICE_SNIPPET = 60;

    private final FeedbackRepository repository;
    private final MemberService memberService;
    private final Clock clock;
    private final ContentSecurityAppService contentSecurity;
    private final NotificationService notificationService;

    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long memberId, FeedbackSubmitCommand cmd) {
        if (repository.countByMemberSince(memberId, LocalDate.now(clock).atStartOfDay()) >= DAILY_LIMIT) {
            throw BizException.tooManyRequests("今天反馈次数已达上限，明天再来吧");
        }
        Feedback f = Feedback.submit(memberId, FeedbackType.ofOrGeneral(cmd.type()), cmd.sourceRef(), cmd.content(),
                cmd.contact(), cmd.images(), cmd.clientInfo(), LocalDateTime.now(clock));
        // 内容安全（A.6）：违规拒绝；微信不可用降级到规则（反馈仅运营可见，不 fail-closed）
        contentSecurity.assertText(memberId, ContentPurpose.FEEDBACK, f.content());
        repository.save(f);
        return f.id();
    }

    // ------------------------------------------------------------------ 用户：我的反馈

    public IPage<MyFeedbackView> mine(Long memberId, Integer pageNum, Integer pageSize) {
        int pn = pageNum(pageNum);
        int ps = pageSize(pageSize);
        FeedbackPage p = repository.pageByMember(memberId, pn, ps);
        Page<MyFeedbackView> out = new Page<>(pn, ps);
        out.setTotal(p.total());
        out.setRecords(p.records().stream().map(FeedbackAppService::mineView).toList());
        return out;
    }

    /** 只能看自己的；别人的反馈一律当不存在（不暴露 id 是否存在）。 */
    public MyFeedbackView mineDetail(Long memberId, Long id) {
        Feedback f = repository.findById(id).filter(x -> x.ownedBy(memberId))
                .orElseThrow(() -> new BizException(404, "反馈不存在"));
        return mineView(f);
    }

    // ------------------------------------------------------------------ 超管

    public IPage<FeedbackView> page(String type, String status, Integer pageNum, Integer pageSize) {
        FeedbackType t = type == null || type.isBlank() ? null : FeedbackType.of(type);
        FeedbackStatus st = status == null || status.isBlank() ? null : FeedbackStatus.of(status);
        int pn = pageNum(pageNum);
        int ps = pageSize(pageSize);
        FeedbackPage p = repository.page(t, st, pn, ps);
        List<Long> memberIds = p.records().stream().map(Feedback::memberId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = memberIds.isEmpty() ? Map.of() : memberService.listByIds(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, m -> m.getName() == null ? "" : m.getName(), (a, b) -> a));
        Page<FeedbackView> out = new Page<>(pn, ps);
        out.setTotal(p.total());
        out.setRecords(p.records().stream().map(f -> new FeedbackView(f.id(), f.memberId(), names.get(f.memberId()),
                f.type().code(), f.sourceRef(), f.content(), f.contact(), f.images(), f.clientInfo(),
                f.status().name(), f.reply(), f.repliedAt(), f.repliedBy(), f.createTime())).toList());
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        Feedback f = load(id);
        f.changeStatus(FeedbackStatus.of(status));
        repository.save(f);
    }

    /**
     * 回复反馈：写回复（NEW → REPLIED），markDone 时一并标记已处理；同事务给提交人发一条站内消息（ref = 反馈 id）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long adminId, Long id, String text, boolean markDone) {
        Feedback f = load(id);
        String r = f.replyBy(adminId, text, LocalDateTime.now(clock));
        if (markDone) f.changeStatus(FeedbackStatus.DONE);
        repository.save(f);
        notificationService.send(new NotificationPayload(f.memberId(), NOTICE_TYPE_REPLY, "你的反馈有回复了",
                snippet(r), String.valueOf(f.id())), "in_app");
    }

    private Feedback load(Long id) {
        return repository.findById(id).orElseThrow(() -> new BizException(404, "反馈不存在"));
    }

    private static MyFeedbackView mineView(Feedback f) {
        return new MyFeedbackView(f.id(), f.type().code(), f.sourceRef(), f.content(), f.images(), f.status().name(),
                f.reply(), f.repliedAt(), f.createTime());
    }

    static String snippet(String reply) {
        return reply.length() <= NOTICE_SNIPPET ? reply : reply.substring(0, NOTICE_SNIPPET) + "…";
    }

    private static int pageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private static int pageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

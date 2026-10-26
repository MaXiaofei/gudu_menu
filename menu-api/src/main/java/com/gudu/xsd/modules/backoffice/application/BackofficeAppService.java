package com.gudu.xsd.modules.backoffice.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.backoffice.application.dto.BackofficeViews;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.model.OperationTarget;
import com.gudu.xsd.modules.backoffice.domain.repository.AccountDirectoryQuery;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.privacy.application.ConsentAppService;
import com.gudu.xsd.modules.privacy.application.ConsentAppService.ConsentHistoryView;
import com.gudu.xsd.modules.contentsecurity.application.ContentReviewAppService;
import com.gudu.xsd.modules.contentsecurity.application.dto.ViolationViews;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import com.gudu.xsd.modules.identity.application.AccountDeactivationAppService;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.application.dto.DeactivationView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 运营后台用例编排（mini-release-compliance F.1–F.4）：账号查询与封禁、注销处理、内容安全违规复核、同意记录查询。
 * 只调各上下文的应用层 API；每个写操作同事务写一条操作日志（admin_operation_log）。
 * 鉴权（OPERATOR / SUPER_ADMIN）在接口层 {@code @RequireOperator}。
 */
@Service
@RequiredArgsConstructor
public class BackofficeAppService {

    static final int MAX_PAGE = 100;

    private final AccountDirectoryQuery directory;
    private final OperationLogRepository logs;
    private final AccountBanAppService bans;
    private final AccountDeactivationAppService deactivations;
    private final AccountQueryApi accountQuery;
    private final ContentReviewAppService contentReview;
    private final ConsentAppService consents;
    private final Clock clock;

    // ================================================================ F.1 账号

    public BackofficeViews.Page<BackofficeViews.AccountItem> accounts(AccountSearch search, int page, int size) {
        int s = clamp(size);
        return new BackofficeViews.Page<>(directory.search(search, offset(page, s), s).stream()
                .map(BackofficeViews.AccountItem::of).toList(), directory.count(search));
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountBanAppService.BanView ban(Long operatorId, Long accountId, String reason) {
        AccountBanAppService.BanView v = bans.ban(operatorId, accountId, reason);
        log(operatorId, OperationAction.BAN, accountId, v.reason(), null);
        return v;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean unban(Long operatorId, Long accountId, String reason) {
        boolean changed = bans.unban(accountId, reason);
        if (!changed) throw new BizException("该账号未被封禁");
        log(operatorId, OperationAction.UNBAN, accountId, reason, null);
        return true;
    }

    // ================================================================ F.2 注销

    public AccountDeactivationAppService.Page deactivations(String status, boolean overdueOnly, int page, int size) {
        return deactivations.page(status, overdueOnly, page, clamp(size));
    }

    @Transactional(rollbackFor = Exception.class)
    public DeactivationView retryDeactivation(Long operatorId, Long recordId) {
        DeactivationView v = deactivations.retry(recordId);
        log(operatorId, OperationAction.RETRY_DEACTIVATION, recordId, null,
                "结果 " + v.status() + (v.lastError() == null ? "" : "：" + v.lastError()));
        return v;
    }

    // ================================================================ F.3 违规复核

    public ViolationViews.Page<ViolationViews.MediaViolation> mediaViolations(String review, String purpose,
                                                                              Long accountId, int page, int size) {
        return contentReview.mediaViolations(review, purpose, accountId, page, size);
    }

    public ViolationViews.Page<ViolationViews.TextViolation> textViolations(Boolean confirmed, String purpose,
                                                                            Long accountId, int page, int size) {
        return contentReview.textViolations(confirmed, purpose, accountId, page, size);
    }

    public Optional<byte[]> quarantinedImage(Long mediaCheckId) {
        return contentReview.quarantinedImage(mediaCheckId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ViolationViews.RestoreResult restoreMedia(Long operatorId, Long id, String reason) {
        requireReason(reason);
        ViolationViews.RestoreResult r = contentReview.restoreMedia(operatorId, id);
        log(operatorId, OperationAction.RESTORE_MEDIA, id, reason,
                r.violation().mediaUrl() + (r.filesRestored() ? "（文件已还原）" : "（隔离目录无文件）"));
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public ViolationViews.MediaViolation confirmMedia(Long operatorId, Long id, String reason) {
        ViolationViews.MediaViolation v = contentReview.confirmMedia(operatorId, id);
        log(operatorId, OperationAction.CONFIRM_MEDIA, id, reason, v.mediaUrl());
        return v;
    }

    @Transactional(rollbackFor = Exception.class)
    public ViolationViews.TextViolation confirmText(Long operatorId, Long id, String reason) {
        ViolationViews.TextViolation v = contentReview.confirmText(operatorId, id);
        log(operatorId, OperationAction.CONFIRM_TEXT, id, reason, v.purpose());
        return v;
    }

    // ================================================================ F.4 同意记录

    public List<ConsentHistoryView> consents(Long accountId) {
        if (accountId == null) throw new BizException("请输入账号 ID");
        return consents.history(accountId);
    }

    // ================================================================ 操作日志

    public BackofficeViews.Page<BackofficeViews.OperationLogItem> operationLogs(String targetType, Long targetId,
                                                                                int page, int size) {
        OperationTarget type = null;
        if (targetType != null && !targetType.isBlank()) {
            try {
                type = OperationTarget.valueOf(targetType.trim());
            } catch (IllegalArgumentException e) {
                throw new BizException("不支持的对象类型：" + targetType);
            }
        }
        int s = clamp(size);
        List<OperationLog> list = logs.page(type, targetId, offset(page, s), s);
        Map<Long, String> names = accountQuery.nicknames(list.stream().map(OperationLog::operatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return new BackofficeViews.Page<>(list.stream().map(l -> new BackofficeViews.OperationLogItem(l.id(),
                l.operatorId(), names.get(l.operatorId()), l.action().name(), l.action().label(),
                l.targetType().name(), l.targetId(), l.reason(), l.detail(), l.createdAt())).toList(),
                logs.count(type, targetId));
    }

    // ================================================================ 内部

    private void log(Long operatorId, OperationAction action, Long targetId, String reason, String detail) {
        logs.save(OperationLog.record(operatorId, action, targetId, reason, detail, LocalDateTime.now(clock)));
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BizException("请填写原因");
        if (reason.trim().length() > 200) throw new BizException("原因不超过 200 字");
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE));
    }

    private static int offset(int page, int size) {
        return (Math.max(1, page) - 1) * size;
    }
}

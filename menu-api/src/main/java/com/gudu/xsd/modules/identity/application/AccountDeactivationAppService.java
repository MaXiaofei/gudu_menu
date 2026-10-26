package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.identity.application.dto.DeactivationView;
import com.gudu.xsd.modules.identity.domain.event.AccountDeactivated;
import com.gudu.xsd.modules.identity.domain.model.AccountDeactivation;
import com.gudu.xsd.modules.identity.domain.model.DeactivationStatus;
import com.gudu.xsd.modules.identity.domain.model.ErasureStepResult;
import com.gudu.xsd.modules.identity.domain.repository.AccountDeactivationRepository;
import com.gudu.xsd.modules.identity.domain.service.WorkingDays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 注销处理记录（mini-release-compliance F.2）。
 *
 * <ol>
 *   <li>与注销同事务（{@link #onDeactivated}）：受理并记第一步「账号匿名化与退出家庭」——注销回滚则记录一并回滚；</li>
 *   <li>注销提交后（{@link #afterDeactivated}）：逐个执行 {@link AccountErasureStep}（系统模式、幂等），
 *       全部成功 DONE，否则 FAILED 并记失败原因；单步异常不影响其他步骤与用户注销结果；</li>
 *   <li>后台重试（{@link #retry}）：只对未完成的记录重跑后续步骤。</li>
 * </ol>
 */
@Slf4j
@Service
public class AccountDeactivationAppService {

    private final AccountDeactivationRepository repository;
    private final List<AccountErasureStep> steps;
    private final Clock clock;

    public AccountDeactivationAppService(AccountDeactivationRepository repository, List<AccountErasureStep> steps,
                                         Clock clock) {
        this.repository = repository;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.clock = clock;
    }

    @EventListener
    public void onDeactivated(AccountDeactivated e) {
        repository.save(AccountDeactivation.accept(e.accountId(), LocalDateTime.now(clock)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void afterDeactivated(AccountDeactivated e) {
        try {
            repository.latestByAccount(e.accountId()).ifPresent(this::process);
        } catch (RuntimeException ex) {
            // 用户注销已成功；后续步骤失败留待后台重试
            log.error("注销后续匿名化执行失败 account={}: {}", e.accountId(), ex.getMessage());
        }
    }

    /** 后台重试：未完成的记录重跑后续匿名化步骤。 */
    public DeactivationView retry(Long recordId) {
        AccountDeactivation d = repository.findById(recordId).orElseThrow(() -> BizException.notFound("注销记录不存在"));
        d.ensureRetryable();
        process(d);
        return view(d, LocalDateTime.now(clock));
    }

    public DeactivationView get(Long recordId) {
        return repository.findById(recordId).map(d -> view(d, LocalDateTime.now(clock)))
                .orElseThrow(() -> BizException.notFound("注销记录不存在"));
    }

    /** 分页：status 为空不过滤；overdueOnly = 只看未完成且超过 15 个工作日的。 */
    public Page page(String status, boolean overdueOnly, int pageNum, int pageSize) {
        DeactivationStatus st = status == null || status.isBlank() ? null : DeactivationStatus.of(status.trim());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime before = overdueOnly ? WorkingDays.overdueThreshold(now) : null;
        int size = Math.max(1, Math.min(pageSize, 100));
        int page = Math.max(1, pageNum);
        List<DeactivationView> records = repository.page(st, before, (page - 1) * size, size).stream()
                .map(d -> view(d, now)).toList();
        return new Page(records, repository.count(st, before), repository.count(null, WorkingDays.overdueThreshold(now)));
    }

    /** @param overdueTotal 全部超期未完成数（后台角标 / 提醒） */
    public record Page(List<DeactivationView> records, long total, long overdueTotal) {
    }

    void process(AccountDeactivation d) {
        if (d.status() == DeactivationStatus.DONE) return;
        List<ErasureStepResult> results = new ArrayList<>();
        for (AccountErasureStep step : steps) {
            try {
                String detail = TenantContextHolder.callAsSystem(() -> step.erase(d.accountId()));
                results.add(ErasureStepResult.ok(step.name(), detail));
            } catch (RuntimeException ex) {
                log.warn("注销匿名化步骤失败 account={} step={}: {}", d.accountId(), step.name(), ex.getMessage());
                results.add(ErasureStepResult.failed(step.name(), ex.getMessage() == null ? ex.getClass().getSimpleName()
                        : ex.getMessage()));
            }
        }
        d.applyAttempt(results, LocalDateTime.now(clock));
        repository.save(d);
    }

    static DeactivationView view(AccountDeactivation d, LocalDateTime now) {
        return new DeactivationView(d.id(), d.accountId(), d.requestedAt(), d.deadline(), d.status().name(),
                d.steps().stream().map(s -> new DeactivationView.Step(s.name(), s.ok(), s.detail())).toList(),
                d.attempts(), d.lastError(), d.completedAt(), d.overdue(now));
    }
}

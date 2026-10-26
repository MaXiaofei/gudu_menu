package com.gudu.xsd.modules.identity.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.identity.domain.service.WorkingDays;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一次账号注销的处理记录（mini-release-compliance F.2）。
 *
 * <ol>
 *   <li>受理（{@link #accept}）：与注销同事务——账号匿名化、退出全部家庭、家庭内档案匿名化已完成，记为第一步；</li>
 *   <li>匿名化其余个人数据（{@link #applyAttempt}）：注销提交后逐步执行（反馈联系方式等），全部成功 → DONE，
 *       任一步失败 → FAILED（后台可重试，重试只重跑后续步骤，各步骤幂等）；</li>
 *   <li>时限：隐私政策承诺 15 个工作日内完成，未完成且超期 {@link #overdue}。</li>
 * </ol>
 */
public final class AccountDeactivation extends AggregateRoot<Long> {

    /** 与注销同事务完成的第一步。 */
    public static final String STEP_ACCOUNT = "账号匿名化与退出家庭";

    private Long id;
    private final Long accountId;
    private final LocalDateTime requestedAt;
    private DeactivationStatus status;
    private List<ErasureStepResult> steps;
    private int attempts;
    private String lastError;
    private LocalDateTime completedAt;

    private AccountDeactivation(Long id, Long accountId, LocalDateTime requestedAt, DeactivationStatus status,
                                List<ErasureStepResult> steps, int attempts, String lastError,
                                LocalDateTime completedAt) {
        this.id = id;
        this.accountId = accountId;
        this.requestedAt = requestedAt;
        this.status = status == null ? DeactivationStatus.PENDING : status;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.attempts = attempts;
        this.lastError = lastError;
        this.completedAt = completedAt;
    }

    /** 受理注销（账号匿名化与家庭处理已在同事务完成）。 */
    public static AccountDeactivation accept(Long accountId, LocalDateTime now) {
        Objects.requireNonNull(accountId, "accountId");
        return new AccountDeactivation(null, accountId, now, DeactivationStatus.PENDING,
                List.of(ErasureStepResult.ok(STEP_ACCOUNT, "手机号 / 微信身份 / 昵称 / 头像已清除，已退出全部家庭")),
                0, null, null);
    }

    public static AccountDeactivation rehydrate(Long id, Long accountId, LocalDateTime requestedAt,
                                                DeactivationStatus status, List<ErasureStepResult> steps,
                                                int attempts, String lastError, LocalDateTime completedAt) {
        return new AccountDeactivation(id, accountId, requestedAt, status, steps, attempts, lastError, completedAt);
    }

    /** 已完成的注销不再重试。 */
    public void ensureRetryable() {
        if (status == DeactivationStatus.DONE) throw new DomainException("该注销已处理完成，无需重试");
    }

    /**
     * 记录一次后续匿名化的执行结果（替换上一次的后续步骤结果，第一步保留）。
     *
     * @param results 各后续步骤结果（无后续步骤时传空列表 → 直接完成）
     */
    public void applyAttempt(List<ErasureStepResult> results, LocalDateTime now) {
        ensureRetryable();
        List<ErasureStepResult> merged = new ArrayList<>();
        for (ErasureStepResult s : steps) {
            if (STEP_ACCOUNT.equals(s.name())) merged.add(s);
        }
        if (merged.isEmpty()) merged.add(ErasureStepResult.ok(STEP_ACCOUNT, null));
        merged.addAll(results == null ? List.of() : results);
        this.steps = List.copyOf(merged);
        this.attempts++;
        ErasureStepResult failed = merged.stream().filter(s -> !s.ok()).findFirst().orElse(null);
        if (failed == null) {
            this.status = DeactivationStatus.DONE;
            this.lastError = null;
            this.completedAt = now;
        } else {
            this.status = DeactivationStatus.FAILED;
            this.lastError = failed.name() + "：" + (failed.detail() == null ? "失败" : failed.detail());
        }
    }

    /** 截止时间（受理后第 15 个工作日结束）。 */
    public LocalDateTime deadline() {
        return WorkingDays.deadline(requestedAt, WorkingDays.DEACTIVATION_LIMIT);
    }

    public boolean overdue(LocalDateTime now) {
        return WorkingDays.overdue(requestedAt, status == DeactivationStatus.DONE, now);
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    @Override
    public Long id() {
        return id;
    }

    public Long accountId() {
        return accountId;
    }

    public LocalDateTime requestedAt() {
        return requestedAt;
    }

    public DeactivationStatus status() {
        return status;
    }

    public List<ErasureStepResult> steps() {
        return steps;
    }

    public int attempts() {
        return attempts;
    }

    public String lastError() {
        return lastError;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }
}

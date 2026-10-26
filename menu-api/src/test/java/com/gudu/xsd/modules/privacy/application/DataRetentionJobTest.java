package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.agent.application.privacy.AgentConversationPurger;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.feedback.application.FeedbackRetentionPurger;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C.1 保存期限清理：期限计算、分批直到清完、配置覆盖 / 关闭、系统模式、单策略失败隔离。 */
class DataRetentionJobTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 3, 30);
    static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    /** 模拟有 total 行待删的表，每批最多删 limit 行；记录每次调用的 cutoff 与是否系统模式。 */
    static class FakePurger implements RetentionPurger {
        final String policy;
        final int days;
        int remaining;
        final List<LocalDateTime> cutoffs = new ArrayList<>();
        final List<Boolean> systemMode = new ArrayList<>();

        FakePurger(String policy, int days, int total) {
            this.policy = policy;
            this.days = days;
            this.remaining = total;
        }

        @Override
        public String policy() {
            return policy;
        }

        @Override
        public int defaultDays() {
            return days;
        }

        @Override
        public int purgeBatch(LocalDateTime cutoff, int limit) {
            cutoffs.add(cutoff);
            systemMode.add(TenantContextHolder.isSystem());
            int n = Math.min(limit, remaining);
            remaining -= n;
            return n;
        }
    }

    static DataRetentionJob job(List<RetentionPurger> purgers, MockEnvironment env, int batch, int maxBatches) {
        return new DataRetentionJob(purgers, env, CLOCK, true, batch, maxBatches);
    }

    @Test
    void 按默认天数算期限_分批删到清完_系统模式运行() {
        FakePurger agent = new FakePurger("agent-conversation", 180, 1234);
        FakePurger feedback = new FakePurger("feedback", 365, 0);
        Map<String, Integer> r = job(List.of(agent, feedback), new MockEnvironment(), 500, 100).runOnce();
        assertThat(agent.remaining).isZero();
        assertThat(agent.cutoffs).hasSize(4).allMatch(c -> c.equals(NOW.minusDays(180)));
        assertThat(agent.systemMode).containsOnly(true);
        assertThat(feedback.cutoffs).containsExactly(NOW.minusDays(365));
        assertThat(r.values()).containsExactly(1234, 0);
        assertThat(TenantContextHolder.isSystem()).as("结束后恢复").isFalse();
    }

    @Test
    void 配置覆盖天数_小于等于0不清理_单次批数上限() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("gudu.retention.days.ai-call-log", "30")
                .withProperty("gudu.retention.days.feedback", "0");
        FakePurger ai = new FakePurger("ai-call-log", 180, 10_000);
        FakePurger feedback = new FakePurger("feedback", 365, 5);
        job(List.of(ai, feedback), env, 100, 3).runOnce();
        assertThat(ai.cutoffs).hasSize(3).allMatch(c -> c.equals(NOW.minusDays(30)));
        assertThat(ai.remaining).as("剩余留到下次").isEqualTo(9_700);
        assertThat(feedback.cutoffs).isEmpty();
    }

    @Test
    void 单个策略失败不影响其他策略() {
        RetentionPurger broken = new FakePurger("ai-call-log", 180, 1) {
            @Override
            public int purgeBatch(LocalDateTime cutoff, int limit) {
                throw new IllegalStateException("db down");
            }
        };
        FakePurger ok = new FakePurger("feedback", 365, 3);
        Map<String, Integer> r = job(List.of(broken, ok), new MockEnvironment(), 500, 10).runOnce();
        assertThat(r.values()).containsExactly(-1, 3);
        assertThat(TenantContextHolder.isSystem()).isFalse();
    }

    @Test
    void 关闭开关_定时任务不执行() {
        FakePurger p = new FakePurger("feedback", 365, 3);
        new DataRetentionJob(List.of(p), new MockEnvironment(), CLOCK, false, 500, 10).scheduled();
        assertThat(p.cutoffs).isEmpty();
    }

    @Test
    void AI对话清理_先删超期消息_再删超期会话及其消息() {
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentConversationPurger p = new AgentConversationPurger(messages, sessions);
        LocalDateTime cutoff = NOW.minusDays(180);
        when(messages.deleteCreatedBefore(cutoff, 500)).thenReturn(500, 20, 0);
        when(sessions.idsUpdatedBefore(cutoff, 500)).thenReturn(List.of("s1", "s2"), List.of());
        when(sessions.hardDelete(List.of("s1", "s2"))).thenReturn(2);
        assertThat(p.purgeBatch(cutoff, 500)).isEqualTo(500);
        assertThat(p.purgeBatch(cutoff, 500)).isEqualTo(20);
        verify(sessions, never()).idsUpdatedBefore(any(), anyInt());
        assertThat(p.purgeBatch(cutoff, 500)).isEqualTo(2);
        verify(messages).deleteBySessions(List.of("s1", "s2"));
        assertThat(p.purgeBatch(cutoff, 500)).isZero();
        assertThat(p.policy()).isEqualTo("agent-conversation");
        assertThat(p.defaultDays()).isEqualTo(180);
    }

    @Test
    void 反馈清理_只删已处理且超过一年() {
        FeedbackRepository repo = mock(FeedbackRepository.class);
        FeedbackRetentionPurger p = new FeedbackRetentionPurger(repo);
        when(repo.deleteDoneBefore(eq(NOW.minusDays(365)), eq(500))).thenReturn(7);
        assertThat(p.purgeBatch(NOW.minusDays(365), 500)).isEqualTo(7);
        assertThat(p.defaultDays()).isEqualTo(365);
    }
}

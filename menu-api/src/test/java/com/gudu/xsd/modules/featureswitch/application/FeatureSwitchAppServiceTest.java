package com.gudu.xsd.modules.featureswitch.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureSwitch;
import com.gudu.xsd.modules.featureswitch.domain.repository.FeatureSwitchRepository;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 咕嘟助手 / 问咕嘟紧急关停开关：默认开、按 key 隔离、切换留痕、短缓存与失效。 */
class FeatureSwitchAppServiceTest {

    static class FakeRepo implements FeatureSwitchRepository {
        final Map<String, FeatureSwitch> state = new ConcurrentHashMap<>();
        final List<FeatureSwitch> logs = new ArrayList<>();
        int loads;
        boolean failing;

        @Override
        public Optional<FeatureSwitch> load(String key) {
            loads++;
            if (failing) throw new IllegalStateException("db down");
            return Optional.ofNullable(state.get(key));
        }

        @Override
        public void save(FeatureSwitch s) {
            state.put(s.key(), s);
            logs.add(0, s);
        }

        @Override
        public List<FeatureSwitch> recentChanges(String key, int limit) {
            return logs.stream().filter(l -> l.key().equals(key)).limit(limit).toList();
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.ofHours(8);
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    FakeRepo repo;
    MutableClock clock;
    FeatureSwitchAppService svc;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        clock = new MutableClock();
        ObjectProvider<AccountQueryApi> accounts = mock(ObjectProvider.class);
        when(accounts.getIfAvailable()).thenReturn(null); // 无账号服务时操作人昵称为空
        svc = new FeatureSwitchAppService(repo, accounts, clock);
    }

    @Test
    void 库里无记录_默认全开() {
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).isTrue();
        assertThat(svc.isEnabled(FeatureKey.AGENT_COOK_ASK)).isTrue();
        var st = svc.publicStatus();
        assertThat(st.agentChat()).isTrue();
        assertThat(st.cookAsk()).isTrue();
        assertThat(svc.viewAll()).hasSize(2).allMatch(v -> v.enabled() && v.updatedBy() == null);
    }

    @Test
    void 两个开关互不影响() {
        svc.set(7L, "agent.chat.enabled", false, "内容风险");
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).isFalse();
        assertThat(svc.isEnabled(FeatureKey.AGENT_COOK_ASK)).isTrue();
        assertThat(svc.publicStatus().agentChat()).isFalse();
        assertThat(svc.publicStatus().cookAsk()).isTrue();
    }

    @Test
    void 关闭立即生效_记录操作人时间原因_再打开() {
        assertThat(svc.isEnabled(FeatureKey.AGENT_COOK_ASK)).isTrue(); // 先缓存「开」
        var v = svc.set(7L, "agent.cook_ask.enabled", false, "  出现违规内容  ");
        assertThat(svc.isEnabled(FeatureKey.AGENT_COOK_ASK)).as("本实例切换立即生效").isFalse();
        assertThat(v.enabled()).isFalse();
        assertThat(v.label()).isEqualTo("问咕嘟");
        assertThat(v.updatedBy()).isEqualTo(7L);
        assertThat(v.updatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 18, 0));
        assertThat(v.reason()).isEqualTo("出现违规内容");

        clock.advance(Duration.ofMinutes(5));
        svc.set(7L, "agent.cook_ask.enabled", true, null);
        assertThat(svc.isEnabled(FeatureKey.AGENT_COOK_ASK)).isTrue();
        assertThat(svc.view(FeatureKey.AGENT_COOK_ASK).recentChanges()).extracting(FeatureSwitch::enabled)
                .containsExactly(true, false);
    }

    @Test
    void 关停必须填原因_未知key拒绝() {
        assertThatThrownBy(() -> svc.set(7L, "agent.chat.enabled", false, "  "))
                .isInstanceOf(BizException.class).hasMessageContaining("原因");
        assertThatThrownBy(() -> svc.set(7L, "no.such.key", true, null)).isInstanceOf(BizException.class);
        assertThat(repo.logs).isEmpty();
    }

    @Test
    void 缓存TTL内沿用_过期后读到新值_且不超过30秒() {
        assertThat(FeatureSwitchAppService.CACHE_TTL).isLessThanOrEqualTo(Duration.ofSeconds(30));
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).isTrue();
        int loads = repo.loads;
        repo.state.put("agent.chat.enabled", FeatureSwitch.change("agent.chat.enabled", false, 9L, null, null, "别的实例"));

        clock.advance(FeatureSwitchAppService.CACHE_TTL.minusSeconds(1));
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).isTrue();
        assertThat(repo.loads).isEqualTo(loads);

        clock.advance(Duration.ofSeconds(1));
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).isFalse();
    }

    @Test
    void 读库失败_沿用上次已知值_从未读到视为开() {
        repo.failing = true;
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).as("从未读到按开").isTrue();
        repo.failing = false;
        clock.advance(Duration.ofMinutes(1));
        svc.set(7L, "agent.chat.enabled", false, "测试");
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).isFalse(); // 读到并缓存「关」
        clock.advance(Duration.ofMinutes(1));
        repo.failing = true;
        assertThat(svc.isEnabled(FeatureKey.AGENT_CHAT)).as("关着的继续关着").isFalse();
    }
}

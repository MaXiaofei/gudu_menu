package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.application.dto.CommunityViews.UserLite;
import com.gudu.xsd.modules.community.domain.model.CommunitySwitch;
import com.gudu.xsd.modules.community.domain.repository.CommunitySwitchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A.12 厨房圈紧急关停开关：默认开、切换留痕、短缓存与失效。 */
class CommunitySwitchAppServiceTest {

    /** 内存仓储：state 可被「其他实例」直接改写，用来验证缓存 TTL。 */
    static class FakeRepo implements CommunitySwitchRepository {
        CommunitySwitch state;
        final List<CommunitySwitch> logs = new ArrayList<>();
        int loads;
        boolean failing;

        @Override
        public Optional<CommunitySwitch> load() {
            loads++;
            if (failing) throw new IllegalStateException("db down");
            return Optional.ofNullable(state);
        }

        @Override
        public void save(CommunitySwitch s) {
            state = s;
            logs.add(0, s);
        }

        @Override
        public List<CommunitySwitch> recentChanges(int limit) {
            return logs.stream().limit(limit).toList();
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
    CommunitySwitchAppService svc;

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        clock = new MutableClock();
        CommunityAssembler assembler = mock(CommunityAssembler.class);
        when(assembler.users(any())).thenReturn(Map.of(7L, new UserLite(7L, "运营小王", null)));
        svc = new CommunitySwitchAppService(repo, assembler, clock);
    }

    @Test
    void 库里无记录_默认开() {
        assertThat(svc.isEnabled()).isTrue();
        assertThat(svc.view().enabled()).isTrue();
        assertThat(svc.view().updatedBy()).isNull();
    }

    @Test
    void 关闭后立即生效_记录操作人时间原因_再打开() {
        assertThat(svc.isEnabled()).isTrue(); // 先把「开」缓存起来
        var v = svc.set(7L, false, "  出现违规内容，紧急关停  ");
        assertThat(svc.isEnabled()).as("本实例切换立即生效，不等缓存过期").isFalse();
        assertThat(v.enabled()).isFalse();
        assertThat(v.updatedBy()).isEqualTo(7L);
        assertThat(v.updatedByName()).isEqualTo("运营小王");
        assertThat(v.updatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 26, 18, 0));
        assertThat(v.reason()).isEqualTo("出现违规内容，紧急关停");

        clock.advance(Duration.ofMinutes(5));
        svc.set(7L, true, null);
        assertThat(svc.isEnabled()).isTrue();
        assertThat(repo.logs).hasSize(2);
        assertThat(svc.view().recentChanges()).extracting(CommunitySwitch::enabled).containsExactly(true, false);
    }

    @Test
    void 其他实例改了开关_本实例缓存TTL内沿用_过期后读到新值_且不超过30秒() {
        assertThat(CommunitySwitchAppService.CACHE_TTL).isLessThanOrEqualTo(Duration.ofSeconds(30));
        assertThat(svc.isEnabled()).isTrue();
        int loads = repo.loads;
        repo.state = CommunitySwitch.change(false, 9L, "别的实例", null, null); // 绕过本实例直接改库

        clock.advance(CommunitySwitchAppService.CACHE_TTL.minusSeconds(1));
        assertThat(svc.isEnabled()).as("TTL 内走缓存").isTrue();
        assertThat(repo.loads).isEqualTo(loads);

        clock.advance(Duration.ofSeconds(1));
        assertThat(svc.isEnabled()).as("过期后重新读库").isFalse();
        assertThat(repo.loads).isEqualTo(loads + 1);
    }

    @Test
    void 读库失败_沿用上次已知值() {
        svc.set(7L, false, null);
        assertThat(svc.isEnabled()).isFalse();
        clock.advance(Duration.ofMinutes(1));
        repo.failing = true;
        assertThat(svc.isEnabled()).as("关着的继续关着").isFalse();
    }

    @Test
    void 原因超长截断_操作人必填() {
        String longReason = "x".repeat(300);
        assertThat(svc.set(7L, false, longReason).reason()).hasSize(CommunitySwitch.MAX_REASON);
        assertThatThrownBy(() -> CommunitySwitch.change(false, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

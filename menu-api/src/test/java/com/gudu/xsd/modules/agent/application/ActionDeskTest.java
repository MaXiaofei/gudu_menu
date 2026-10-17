package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.agent.application.assess.FixtureKitchen;
import com.gudu.xsd.modules.agent.application.assess.InMemoryActionStore;
import com.gudu.xsd.modules.agent.application.tools.ToolOutcome;
import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionDeskTest {

    private static Map<String, Object> cookArgs() {
        return Map.of("dishId", 3L, "dishName", "麻婆豆腐", "servings", 4, "eaterIds", List.of(), "overrides", List.of(),
                "_key", "cooked:3");
    }

    @Test
    void 做完回写_确认后扣余量_撤销恢复() {
        AgentTestKit kit = AgentTestKit.create();
        PendingAction a = kit.desk.register(AgentTestKit.ME, "s1", "m1", "mark_cooked",
                new ToolOutcome.Draft("做完了：麻婆豆腐", cookArgs(), Map.of())).action();
        assertThat(kit.kitchen.effectValue("pantry", "北豆腐", "status")).isEqualTo("ENOUGH");
        ActionDesk.Confirmed c = kit.desk.confirm(AgentTestKit.ME, a.actionId());
        assertThat(kit.kitchen.effectValue("pantry", "北豆腐", "status")).isEqualTo("OUT");
        assertThat(((Number) kit.kitchen.effectValue("pantry", "肉末", "amount")).intValue()).isEqualTo(200);

        kit.desk.undo(AgentTestKit.ME, c.undoToken());
        assertThat(kit.kitchen.effectValue("pantry", "北豆腐", "status")).isEqualTo("ENOUGH");
        assertThat(((Number) kit.kitchen.effectValue("pantry", "肉末", "amount")).intValue()).isEqualTo(300);
        assertThat(kit.store.find(a.actionId()).orElseThrow().status()).isEqualTo(ActionStatus.UNDONE);
        assertThatThrownBy(() -> kit.desk.undo(AgentTestKit.ME, c.undoToken())).as("撤销凭据只能用一次")
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 重复确认只执行一次_他人不能确认() {
        AgentTestKit kit = AgentTestKit.create();
        PendingAction a = kit.desk.register(AgentTestKit.ME, "s1", "m1", "mark_cooked",
                new ToolOutcome.Draft("做完了", cookArgs(), Map.of())).action();
        assertThatThrownBy(() -> kit.desk.confirm(9999L, a.actionId())).isInstanceOf(DomainException.class)
                .hasMessageContaining("无权");
        kit.desk.confirm(AgentTestKit.ME, a.actionId());
        assertThatThrownBy(() -> kit.desk.confirm(AgentTestKit.ME, a.actionId())).isInstanceOf(DomainException.class);
        assertThat(kit.kitchen.writes()).isEqualTo(1);
    }

    @Test
    void 确认过期_拒绝且不写入() {
        Map<String, Object> fixture = new java.util.LinkedHashMap<>(AgentTestKit.fixture());
        FixtureKitchen kitchen = new FixtureKitchen(fixture, AgentTestKit.OM);
        InMemoryActionStore store = new InMemoryActionStore();
        LocalDateTime created = kitchen.now().minusMinutes(6);
        store.save(new PendingAction("act_old", "s1", "m1", AgentTestKit.ME, "mark_cooked", "做完了", cookArgs(), Map.of(),
                created, created.plus(PendingAction.TTL), ActionStatus.PENDING, null));
        ActionDesk desk = new ActionDesk(store, kitchen);
        assertThatThrownBy(() -> desk.confirm(AgentTestKit.ME, "act_old")).isInstanceOf(DomainException.class)
                .hasMessageContaining("过期");
        assertThat(store.find("act_old").orElseThrow().status()).isEqualTo(ActionStatus.EXPIRED);
        assertThat(kitchen.writes()).isZero();
    }

    @Test
    void 同目标新卡片替代旧卡片() {
        AgentTestKit kit = AgentTestKit.create();
        PendingAction first = kit.desk.register(AgentTestKit.ME, "s1", "m1", "mark_cooked",
                new ToolOutcome.Draft("3 人份", cookArgs(), Map.of())).action();
        ActionDesk.Registered second = kit.desk.register(AgentTestKit.ME, "s1", "m2", "mark_cooked",
                new ToolOutcome.Draft("4 人份", cookArgs(), Map.of()));
        assertThat(second.superseded()).containsExactly(first.actionId());
        assertThat(kit.store.find(first.actionId()).orElseThrow().status()).isEqualTo(ActionStatus.SUPERSEDED);
    }

    @Test
    void 沙箱隔离_两个实例互不影响_夹具原件不变() {
        Map<String, Object> fixture = AgentTestKit.fixture();
        FixtureKitchen a = new FixtureKitchen(fixture, AgentTestKit.OM);
        FixtureKitchen b = new FixtureKitchen(fixture, AgentTestKit.OM);
        a.addPantry(List.of(new Kitchen.PantryLine("西兰花", "1", "颗", null)));
        assertThat(a.effectValue("pantry", "西兰花", "status")).isEqualTo("ENOUGH");
        assertThat(b.effectValue("pantry", "西兰花", "status")).isNull();
        assertThat(new FixtureKitchen(fixture, AgentTestKit.OM).effectValue("pantry", "西兰花", "status")).isNull();
        assertThat(b.writes()).isZero();
    }
}

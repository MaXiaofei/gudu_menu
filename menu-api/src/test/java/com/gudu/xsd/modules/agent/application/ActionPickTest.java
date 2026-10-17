package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** undo_action / cancel_action 的 action 参数：模型常写成描述而不是 id（首版基线里撤销类用例因此失败）。 */
class ActionPickTest {

    private static PendingAction a(String id, String tool, String title, Map<String, Object> args) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 17, 40);
        return new PendingAction(id, "s1", "m1", 1L, tool, title, args, Map.of(), now, now.plusMinutes(5),
                ActionStatus.DONE, "u-" + id);
    }

    private final PendingAction cooked = a("act_1", "mark_cooked", "记录麻婆豆腐 · 3 人份", Map.of("dish", "麻婆豆腐"));
    private final PendingAction menu = a("act_2", "add_to_menu", "加入「今晚的饭」· 1 道菜", Map.of("dishes", List.of("凉拌菠菜")));

    @Test
    void 空引用与id精确命中() {
        assertThat(ActionDesk.pick(List.of(cooked, menu), null)).containsExactly(cooked, menu);
        assertThat(ActionDesk.pick(List.of(cooked, menu), "act_2")).containsExactly(menu);
    }

    @Test
    void 描述式引用按词命中() {
        assertThat(ActionDesk.pick(List.of(cooked, menu), "mark_cooked 麻婆豆腐")).containsExactly(cooked);
        assertThat(ActionDesk.pick(List.of(cooked, menu), "把凉拌菠菜加到今晚的饭")).containsExactly(menu);
    }

    @Test
    void 都不中时只有一个候选就是它_多个候选则不猜() {
        assertThat(ActionDesk.pick(List.of(cooked), "刚才那个")).containsExactly(cooked);
        assertThat(ActionDesk.pick(List.of(cooked, menu), "刚才那个")).isEmpty();
    }
}

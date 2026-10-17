package com.gudu.xsd.modules.agent.domain;

import com.gudu.xsd.modules.agent.domain.service.ActionClaimGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActionClaimGuardTest {

    @Test
    void 声称已生成卡片但本轮没有动作() {
        // 评测 write_shopping.unit.002 的真实回复形态
        assertThat(ActionClaimGuard.claimWithoutAction("给你加了半打（6个）鸡蛋，等确认：· 鸡蛋 6 个 你点确认后，它才会加进采购单。",
                0, List.of("get_pantry", "get_shopping_list"))).isEqualTo("点确认后");
        assertThat(ActionClaimGuard.claimWithoutAction("采购卡片已经生成，点确认才会正式加到采购单里。", 0, List.of()))
                .isNotNull();
    }

    @Test
    void 真有动作或撤销取消时不算() {
        assertThat(ActionClaimGuard.claimWithoutAction("卡片已生成，点确认就加好。", 1, List.of("add_to_shopping"))).isNull();
        assertThat(ActionClaimGuard.claimWithoutAction("已撤销了。", 0, List.of("undo_action"))).isNull();
        assertThat(ActionClaimGuard.claimWithoutAction("要不要我把它加进采购单？", 0, List.of())).isNull();
    }
}

package com.gudu.xsd.modules.agent.infrastructure;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.common.security.AiQuotaGate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 咕嘟配额按登录账号计（不是成员档案），超限或计数设施故障都返回 false → 退回规则回复。 */
class RedisAgentQuotaTest {

    @Test
    void 委托闸门_按账号和AGENT类别() {
        AiQuotaGate gate = mock(AiQuotaGate.class);
        when(gate.tryConsume(AiQuotaCategory.AGENT, 77L)).thenReturn(true, false);
        RedisAgentQuota quota = new RedisAgentQuota(gate);
        assertThat(quota.consume(77L, 50)).isTrue();
        assertThat(quota.consume(77L, 50)).as("闸门拒绝（超限或 Redis 故障）→ 规则回复").isFalse();
        verify(gate, org.mockito.Mockito.times(2)).tryConsume(AiQuotaCategory.AGENT, 77L);
    }
}

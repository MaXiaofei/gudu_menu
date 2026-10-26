package com.gudu.xsd.modules.backoffice.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.AgentTraceAppService;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.FullTrace;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.repository.AccountDirectoryQuery;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.contentsecurity.application.ContentReviewAppService;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import com.gudu.xsd.modules.identity.application.AccountDeactivationAppService;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.privacy.application.ConsentAppService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 查看咕嘟对话全文：必须填写原因，成功后写操作日志（对象 = 对话所属账号，调用链 id 记在详情里）。 */
class AgentTraceFullAuditTest {

    final OperationLogRepository logs = mock(OperationLogRepository.class);
    final AgentTraceAppService traces = mock(AgentTraceAppService.class);
    final BackofficeAppService svc = new BackofficeAppService(mock(AccountDirectoryQuery.class), logs,
            mock(AccountBanAppService.class), mock(AccountDeactivationAppService.class), mock(AccountQueryApi.class),
            mock(ContentReviewAppService.class), mock(ConsentAppService.class), traces,
            Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void 不填原因_拒绝_不读取也不记日志() {
        assertThatThrownBy(() -> svc.agentTraceFull(1L, "t1", "  ")).isInstanceOf(BizException.class)
                .hasMessageContaining("原因");
        assertThatThrownBy(() -> svc.agentTraceFull(1L, "t1", null)).isInstanceOf(BizException.class);
        verify(traces, never()).full(any());
        verify(logs, never()).save(any());
    }

    @Test
    void 填写原因_返回全文并写操作日志() {
        FullTrace full = new FullTrace(7L, null, List.of());
        when(traces.full("t1")).thenReturn(full);

        assertThat(svc.agentTraceFull(1L, "t1", "用户投诉回答异常")).isSameAs(full);

        ArgumentCaptor<OperationLog> cap = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(cap.capture());
        OperationLog l = cap.getValue();
        assertThat(l.operatorId()).isEqualTo(1L);
        assertThat(l.action()).isEqualTo(OperationAction.VIEW_AGENT_FULL);
        assertThat(l.targetId()).isEqualTo(7L);
        assertThat(l.reason()).isEqualTo("用户投诉回答异常");
        assertThat(l.detail()).contains("t1");
    }
}

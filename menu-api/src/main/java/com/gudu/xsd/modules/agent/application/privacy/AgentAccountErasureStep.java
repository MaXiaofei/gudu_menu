package com.gudu.xsd.modules.agent.application.privacy;

import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号注销后续匿名化（隐私政策 §六、§七.6）：删除该账号的 AI 对话（消息 + 会话，含已软删除的），
 * 调用链去标识（清首句与片段输入输出、解除档案关联，统计保留）。
 *
 * <p><b>归属语义</b>：agent_session / agent_message / agent_trace 的 member_id 是<b>成员档案 id</b>
 * （写入处取 CurrentMember.id()，不是账号 id）。账号 id 与档案 id 是两张表各自的自增（存量账号沿用旧 member.id，
 * 新账号与新档案的 id 可能撞号），所以必须经 {@link FamilyQueryApi#profileIdsOf} 取本人档案 id 再删，
 * 绝不能拿账号 id 直接当 member_id（会误删他人数据）。注销匿名化保留档案的 account_id，后台重试仍能找到。
 * 幂等：无数据时全部为 0。
 */
@Component
@RequiredArgsConstructor
public class AgentAccountErasureStep implements AccountErasureStep {

    private final AgentMessageRepository messages;
    private final AgentSessionRepository sessions;
    private final AgentTraceRepository traces;
    private final FamilyQueryApi families;

    @Override
    public String name() {
        return "AI 对话删除";
    }

    @Override
    public String erase(Long accountId) {
        List<Long> profileIds = families.profileIdsOf(accountId);
        if (profileIds.isEmpty()) return "无本人成员档案，无对话可删";
        int msg = messages.deleteByMembers(profileIds);
        List<String> ids = sessions.idsByMembers(profileIds);
        if (!ids.isEmpty()) messages.deleteBySessions(ids);
        int ses = sessions.hardDelete(ids);
        int tr = traces.anonymizeByMembers(profileIds);
        return "删除消息 " + msg + " 条、会话 " + ses + " 个，调用链去标识 " + tr + " 条";
    }
}

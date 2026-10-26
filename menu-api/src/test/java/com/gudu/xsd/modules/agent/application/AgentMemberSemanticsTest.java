package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.modules.agent.application.privacy.AgentProfileMergedListener;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.family.domain.event.ProfileMerged;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.FamilyRoleCache;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * agent_session / agent_message / agent_trace 的 member_id 是成员档案 id（CurrentMember），不是账号 id：
 * 账号合并时随档案改指；称呼脱敏取档案所在家庭的全部成员。
 */
class AgentMemberSemanticsTest {

    static FamilyMemberProfile profile(long id, long familyId, Long accountId, String name) {
        return FamilyMemberProfile.rehydrate(id, FamilyId.of(familyId), accountId, name, null, null, null, null);
    }

    @Test
    void 合并_重复档案的AI对话改指目标档案() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentTraceRepository traces = mock(AgentTraceRepository.class);

        new AgentProfileMergedListener(sessions, messages, traces).on(new ProfileMerged(22L, 12L));

        verify(sessions).reassignMember(22L, 12L);
        verify(messages).reassignMember(22L, 12L);
        verify(traces).reassignMember(22L, 12L);
    }

    @Test
    void 称呼范围_档案所在家庭全部成员_已注销成员不计() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        FamilyQueryApi api = new FamilyQueryApi(mock(FamilyRepository.class), profiles, mock(FamilyRoleCache.class));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile(7, 10, 1L, "阿美")));
        when(profiles.findByFamily(10L)).thenReturn(List.of(profile(7, 10, 1L, "阿美"), profile(8, 10, null, "糯糯"),
                profile(9, 10, 2L, FamilyMemberProfile.DEACTIVATED_NAME)));

        assertThat(api.memberNamesAround(7L)).containsExactly("阿美", "糯糯");
    }

    @Test
    void 称呼范围_家庭取不到_退回账号全部家庭的成员() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        FamilyQueryApi api = new FamilyQueryApi(mock(FamilyRepository.class), profiles, mock(FamilyRoleCache.class));
        FamilyMemberProfile me = profile(7, 10, 1L, "阿美");
        when(profiles.findById(7L)).thenReturn(Optional.of(me));
        when(profiles.findByFamily(10L)).thenReturn(List.of()).thenReturn(List.of(me, profile(8, 10, null, "糯糯")));
        when(profiles.findByAccount(1L)).thenReturn(List.of(me));

        assertThat(api.memberNamesAround(7L)).contains("阿美", "糯糯");
        assertThat(api.memberNamesAround(99L)).isEmpty();
    }
}

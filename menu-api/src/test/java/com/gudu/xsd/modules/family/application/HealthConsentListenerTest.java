package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.HealthProfile;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.privacy.domain.event.SensitiveConsentWithdrawn;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B.2 撤回即删除：只删该账号本人档案与其提供的档案的健康字段；儿童同意只影响儿童档案。 */
class HealthConsentListenerTest {

    static final long A = 7L;

    ProfileRepository repo;
    HealthConsentListener listener;
    FamilyMemberProfile mine;
    FamilyMemberProfile baby;
    FamilyMemberProfile grandma;

    static FamilyMemberProfile profile(long id, Long account, Map<String, Object> health, String goal) {
        return FamilyMemberProfile.rehydrate(id, FamilyId.of(1L), account, "成员" + id, null,
                health == null ? null : new HealthProfile(health), goal, null);
    }

    @BeforeEach
    void setUp() {
        repo = mock(ProfileRepository.class);
        listener = new HealthConsentListener(repo);
        mine = profile(1, A, Map.of("height", 170), "LOSE");
        baby = profile(2, null, Map.of("ageMonth", 10, "audiences", List.of("宝宝辅食")), null);
        grandma = profile(3, null, Map.of("audiences", List.of("高血压")), null);
        when(repo.findByAccount(A)).thenReturn(List.of(mine));
        when(repo.findHealthProvidedBy(A)).thenReturn(List.of(mine, baby, grandma));
    }

    @Test
    void 撤回健康档案同意_清空本人与其提供的全部档案健康字段_档案本身保留() {
        listener.onWithdrawn(new SensitiveConsentWithdrawn(A, ConsentDocType.SENSITIVE_HEALTH));
        for (FamilyMemberProfile p : List.of(mine, baby, grandma)) {
            assertThat(p.health()).isNull();
            assertThat(p.goal()).isNull();
            assertThat(p.activityLevel()).isNull();
            assertThat(p.displayName()).isNotNull();
            verify(repo).save(p);
        }
    }

    @Test
    void 撤回儿童同意_只清儿童档案() {
        listener.onWithdrawn(new SensitiveConsentWithdrawn(A, ConsentDocType.CHILD_INFO));
        assertThat(baby.health()).isNull();
        assertThat(mine.health()).isNotNull();
        assertThat(grandma.health()).isNotNull();
        verify(repo).save(baby);
        verify(repo, never()).save(mine);
        verify(repo, never()).save(grandma);
    }

    @Test
    void 无健康信息的档案不写库_账号合并改指提供人() {
        when(repo.findByAccount(8L)).thenReturn(List.of(profile(9, 8L, null, null)));
        when(repo.findHealthProvidedBy(8L)).thenReturn(List.of());
        listener.onWithdrawn(new SensitiveConsentWithdrawn(8L, ConsentDocType.SENSITIVE_HEALTH));
        verify(repo, never()).save(any());
        listener.onMerged(new AccountMerged(8L, 9L));
        verify(repo).reassignHealthProvider(8L, 9L);
    }
}

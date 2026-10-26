package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.family.application.FamilyPersonalData;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.HealthProfile;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.privacy.application.dto.ExportLinkView;
import com.gudu.xsd.modules.privacy.domain.repository.ExportGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C.2 导出我的数据：限频、链接、按序汇总、系统模式、只含本人档案。 */
class PersonalDataExportAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 9, 0);

    /** 内存限频 / 凭证。 */
    static class MemGuard implements ExportGuard {
        final Map<Long, Long> counts = new HashMap<>();
        final Map<String, Long> tokens = new HashMap<>();

        @Override
        public long incrementToday(Long accountId) {
            return counts.merge(accountId, 1L, Long::sum);
        }

        @Override
        public String issueToken(Long accountId, Duration ttl) {
            String t = "tok" + tokens.size() + "-abcdefghijklmnopqrstuvwxyz";
            tokens.put(t, accountId);
            return t;
        }

        @Override
        public Optional<Long> resolveToken(String token) {
            return Optional.ofNullable(tokens.get(token));
        }
    }

    static PersonalDataSection section(String key, int order, List<PersonalDataSubject> seen, List<Boolean> system) {
        return new PersonalDataSection() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public Object export(PersonalDataSubject s) {
                seen.add(s);
                system.add(TenantContextHolder.isSystem());
                return List.of(key + ":" + s.accountId());
            }
        };
    }

    MemGuard guard;
    FamilyQueryApi familyQuery;
    List<PersonalDataSubject> seen;
    List<Boolean> system;
    PersonalDataExportAppService svc;

    @BeforeEach
    void setUp() {
        guard = new MemGuard();
        familyQuery = mock(FamilyQueryApi.class);
        when(familyQuery.profileIdsOf(7L)).thenReturn(List.of(70L, 71L));
        seen = new ArrayList<>();
        system = new ArrayList<>();
        svc = new PersonalDataExportAppService(
                List.of(section("reviews", 60, seen, system), section("account", 10, seen, system)),
                familyQuery, guard, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void 导出_按order汇总_主体含本人全部档案_系统模式执行() {
        Map<String, Object> out = svc.export(7L);
        assertThat(out.keySet()).containsExactly("formatVersion", "exportedAt", "accountId", "note", "account", "reviews");
        assertThat(out.get("account")).isEqualTo(List.of("account:7"));
        assertThat(seen).allMatch(s -> s.accountId() == 7L && s.profileIds().equals(List.of(70L, 71L)));
        assertThat(system).containsOnly(true);
        assertThat(TenantContextHolder.isSystem()).isFalse();
    }

    @Test
    void 每天3次_直接导出与生成链接合计_第4次429() {
        svc.export(7L);
        ExportLinkView link = svc.createLink(7L);
        svc.export(7L);
        assertThatThrownBy(() -> svc.export(7L)).isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(BizException.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> svc.createLink(7L)).isInstanceOf(BizException.class);
        assertThat(svc.export(8L)).as("按账号计数").isNotNull();
        // 凭链接下载不再计数
        assertThat(svc.exportByToken(link.path().substring(link.path().indexOf("token=") + 6)).get("accountId"))
                .isEqualTo(7L);
    }

    @Test
    void 下载链接_30分钟有效_路径带token_无效凭证404() {
        ExportLinkView link = svc.createLink(7L);
        assertThat(link.path()).startsWith("/account/export/download?token=");
        assertThat(link.expiresAt()).isEqualTo(NOW.plusMinutes(30));
        assertThatThrownBy(() -> svc.exportByToken("nope")).isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(BizException.NOT_FOUND));
        assertThatThrownBy(() -> svc.export(null)).isInstanceOf(BizException.class);
    }

    @Test
    void 家庭一节_只含本人档案_不含其他成员健康信息() {
        FamilyRepository families = mock(FamilyRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        Family f = Family.create(new FamilyName("我的家"), 7L, NOW);
        f.identify(1L);
        f.admit(8L, NOW);
        when(families.findActiveByAccount(7L)).thenReturn(List.of(f));
        FamilyMemberProfile mine = FamilyMemberProfile.rehydrate(70L, FamilyId.of(1L), 7L, "我", null,
                new HealthProfile(Map.of("height", 170)), "LOSE", null);
        when(profiles.findByAccount(7L)).thenReturn(List.of(mine));
        FamilyPersonalData section = new FamilyPersonalData(families, profiles);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> out = (List<Map<String, Object>>) section.export(new PersonalDataSubject(7L, List.of(70L)));
        assertThat(out).hasSize(1);
        Map<String, Object> fam = out.get(0);
        assertThat(fam).containsEntry("name", "我的家").containsEntry("myRole", "OWNER").containsEntry("memberCount", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> my = (Map<String, Object>) fam.get("myProfile");
        assertThat(my).containsEntry("profileId", 70L).containsEntry("healthProfile", Map.of("height", 170));
        assertThat(fam.toString()).doesNotContain("members");
    }
}

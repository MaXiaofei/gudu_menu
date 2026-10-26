package com.gudu.xsd.modules.preference.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.preference.domain.model.UserPreference;
import com.gudu.xsd.modules.preference.domain.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A.8 个性化推荐开关：默认开启；按登录账号存。 */
class UserPreferenceAppServiceTest {

    UserPreferenceRepository repo;
    UserPreferenceAppService svc;

    @BeforeEach
    void setUp() {
        repo = mock(UserPreferenceRepository.class);
        svc = new UserPreferenceAppService(repo);
    }

    @Test
    void 无记录_默认开启() {
        when(repo.findByMemberId(1L)).thenReturn(Optional.empty());
        assertThat(svc.get(1L).personalizedRecommend()).isTrue();
        assertThat(svc.personalizedRecommendEnabled(1L)).isTrue();
        assertThat(svc.personalizedRecommendEnabled(null)).isTrue();
    }

    @Test
    void 关闭后保存_读回关闭() {
        when(repo.findByMemberId(1L)).thenReturn(Optional.empty());
        assertThat(svc.update(1L, false).personalizedRecommend()).isFalse();
        ArgumentCaptor<UserPreference> saved = ArgumentCaptor.forClass(UserPreference.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().memberId()).isEqualTo(1L);
        assertThat(saved.getValue().personalizedRecommend()).isFalse();

        when(repo.findByMemberId(1L)).thenReturn(Optional.of(UserPreference.rehydrate(1L, false)));
        assertThat(svc.personalizedRecommendEnabled(1L)).isFalse();
    }

    @Test
    void 更新_参数为空拒绝() {
        when(repo.findByMemberId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.update(1L, null)).isInstanceOf(BizException.class);
        verify(repo, never()).save(any());
    }
}

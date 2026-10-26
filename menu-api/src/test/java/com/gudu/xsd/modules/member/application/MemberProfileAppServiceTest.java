package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberProfileAppServiceTest {

    @Test
    void me_返回脱敏资料() {
        MemberProfileRepository repo = mock(MemberProfileRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(
                MemberProfile.rehydrate(1L, "张爸爸", "/gudu/uploads/a.jpg", "13800000001", false)));
        assertThat(new MemberProfileAppService(repo, mock(ContentSecurityAppService.class)).me(1L))
                .isEqualTo(new MemberMeView(1L, "张爸爸", "/gudu/uploads/a.jpg", "138****0001", false));
    }

    @Test
    void update_只改传了的字段_保存() {
        MemberProfileRepository repo = mock(MemberProfileRepository.class);
        MemberProfile p = MemberProfile.rehydrate(1L, "张爸爸", "/gudu/uploads/a.jpg", null, false);
        when(repo.findById(1L)).thenReturn(Optional.of(p));
        MemberMeView v = new MemberProfileAppService(repo, mock(ContentSecurityAppService.class)).update(1L, "老张", null);
        assertThat(v.name()).isEqualTo("老张");
        assertThat(v.avatarUrl()).isEqualTo("/gudu/uploads/a.jpg");
        verify(repo).save(p);
        new MemberProfileAppService(repo, mock(ContentSecurityAppService.class)).update(1L, null, "/uploads/b.png");
        assertThat(p.avatarUrl()).isEqualTo("/uploads/b.png");
    }

    @Test
    void update_非法头像_不保存_成员不存在报错() {
        MemberProfileRepository repo = mock(MemberProfileRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(MemberProfile.rehydrate(1L, "x", null, null, false)));
        MemberProfileAppService svc = new MemberProfileAppService(repo, mock(ContentSecurityAppService.class));
        assertThatThrownBy(() -> svc.update(1L, null, "http://x.com/a.png")).isInstanceOf(DomainException.class);
        verify(repo, never()).save(any());
        assertThatThrownBy(() -> svc.me(9L)).isInstanceOf(BizException.class).hasMessageContaining("成员不存在");
    }

    @Test
    void update_内容安全_只检变化的昵称头像_不通过不保存() {
        MemberProfileRepository repo = mock(MemberProfileRepository.class);
        ContentSecurityAppService cs = mock(ContentSecurityAppService.class);
        MemberProfile p = MemberProfile.rehydrate(1L, "张爸爸", "/gudu/uploads/a.jpg", null, false);
        when(repo.findById(1L)).thenReturn(Optional.of(p));
        MemberProfileAppService svc = new MemberProfileAppService(repo, cs);

        // 昵称、头像都没变：不检测
        svc.update(1L, "张爸爸", "/gudu/uploads/a.jpg");
        verify(cs, never()).assertText(any(), any(), any());
        verify(cs, never()).assertImageUsable(any(), any(), any());

        // 改头像：检头像；清除头像（空串）不检
        svc.update(1L, null, "/gudu/uploads/b.jpg");
        verify(cs).assertImageUsable(1L, "/gudu/uploads/b.jpg", ContentPurpose.AVATAR);
        svc.update(1L, null, "");
        verify(cs).assertImageUsable(any(), any(), any());

        // 昵称违规：抛 451 且不保存
        doThrow(BizException.contentBlocked("昵称未通过内容安全检测，换一个试试"))
                .when(cs).assertText(1L, ContentPurpose.NICKNAME, "坏昵称");
        MemberProfileRepository repo2 = mock(MemberProfileRepository.class);
        when(repo2.findById(1L)).thenReturn(Optional.of(MemberProfile.rehydrate(1L, "x", null, null, false)));
        MemberProfileAppService svc2 = new MemberProfileAppService(repo2, cs);
        assertThatThrownBy(() -> svc2.update(1L, "坏昵称", null)).isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(BizException.CONTENT_BLOCKED));
        verify(repo2, never()).save(any());
    }
}

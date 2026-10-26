package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarBlockedListenerTest {

    static final String URL = "/gudu/uploads/original/20261026/a.jpg";
    static final String THUMB = "/gudu/uploads/thumbnail/20261026/a.jpg";

    @Test
    void 违规图片_按原图与缩略图地址找到头像_清空并保存() {
        MemberProfileRepository repo = mock(MemberProfileRepository.class);
        MemberProfile a = MemberProfile.rehydrate(1L, "张爸爸", URL, null, false);
        MemberProfile b = MemberProfile.rehydrate(2L, "李妈妈", THUMB, null, false);
        when(repo.findByAvatarUrls(List.of(URL, THUMB))).thenReturn(List.of(a, b));

        new AvatarBlockedListener(repo).on(new MediaBlockedEvent(URL, 1L, "AVATAR", 20002));

        assertThat(a.avatarUrl()).isNull();
        assertThat(b.avatarUrl()).isNull();
        verify(repo).save(a);
        verify(repo).save(b);
    }

    @Test
    void 没有成员引用_不保存() {
        MemberProfileRepository repo = mock(MemberProfileRepository.class);
        when(repo.findByAvatarUrls(any())).thenReturn(List.of());
        new AvatarBlockedListener(repo).on(new MediaBlockedEvent(URL, 1L, "UPLOAD", 20002));
        verify(repo, never()).save(any());
    }
}

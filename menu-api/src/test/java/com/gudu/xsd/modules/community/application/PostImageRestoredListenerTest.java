package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.domain.model.ModerationAction;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaRestoredEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 图片复核恢复不得绕过举报 / 人工处置：只有隐藏原因仅为「图片未通过审核」时才恢复公开。 */
class PostImageRestoredListenerTest {

    static final String URL = PostImageBlockedListenerTest.URL;

    private MediaRestoredEvent restore(Post p) {
        PostRepository posts = mock(PostRepository.class);
        when(posts.findByImageUrls(any())).thenReturn(List.of(p));
        MediaRestoredEvent ev = new MediaRestoredEvent(URL, PostImageBlockedListenerTest.AUTHOR, "POST");
        new PostImageRestoredListener(posts).on(ev);
        return ev;
    }

    @Test
    void 仅因图片隐藏_恢复后公开() {
        Post p = PostImageBlockedListenerTest.published(1L);
        p.hideForBlockedImage();
        MediaRestoredEvent ev = restore(p);
        assertThat(p.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(p.imageBlocked()).isFalse();
        assertThat(ev.notices()).isEmpty();
    }

    @Test
    void 图片隐藏后又被举报达阈值_恢复图片仍保持隐藏并提示() {
        Post p = PostImageBlockedListenerTest.published(1L);
        p.hideForBlockedImage();
        assertThat(p.hideByReports()).as("已因图片隐藏时登记举报原因").isTrue();
        assertThat(p.hideByReports()).as("幂等").isFalse();
        MediaRestoredEvent ev = restore(p);
        assertThat(p.status()).isEqualTo(PostStatus.HIDDEN);
        assertThat(p.imageBlocked()).isFalse();
        assertThat(p.reviewed()).isFalse();
        assertThat(ev.notices()).singleElement().asString().contains("仍有其他处置");
    }

    @Test
    void 先举报隐藏再图片违规_恢复图片仍保持隐藏() {
        Post p = PostImageBlockedListenerTest.published(1L);
        p.hideByReports();
        p.hideForBlockedImage();
        restore(p);
        assertThat(p.status()).isEqualTo(PostStatus.HIDDEN);
    }

    @Test
    void 运营人工隐藏后恢复图片_仍隐藏_人工通过后才公开() {
        Post p = PostImageBlockedListenerTest.published(1L);
        p.hideForBlockedImage();
        p.moderate(ModerationAction.HIDE);
        restore(p);
        assertThat(p.status()).isEqualTo(PostStatus.HIDDEN);
        p.moderate(ModerationAction.APPROVE);
        assertThat(p.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(p.hiddenByOthers()).isFalse();
    }
}

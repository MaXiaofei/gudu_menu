package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.domain.model.ModerationVerdict;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.PostVisibility;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostImageBlockedListenerTest {

    static final String URL = "/gudu/uploads/original/20261026/a.jpg";
    static final long AUTHOR = 7L;

    static Post published(long id) {
        Post p = Post.publish(AUTHOR, "清蒸鲈鱼", "", List.of(URL), null, List.of(), PostVisibility.PUBLIC, null, null,
                null, LocalDateTime.of(2026, 10, 26, 12, 0));
        p.applyVerdict(ModerationVerdict.low());
        p.identify(id);
        return p;
    }

    @Test
    void 含违规图的帖子_隐藏回复核队列_他人不可见_作者看到图片未通过审核() {
        PostRepository posts = mock(PostRepository.class);
        Post p = published(1L);
        when(posts.findByImageUrls(List.of(URL, URL.replace("/original/", "/thumbnail/")))).thenReturn(List.of(p));

        new PostImageBlockedListener(posts).on(new MediaBlockedEvent(URL, AUTHOR, "POST", 20002));

        assertThat(p.status()).isEqualTo(PostStatus.HIDDEN);
        assertThat(p.reviewed()).as("回到人工复核队列").isFalse();
        assertThat(p.riskReasons()).first().isEqualTo(Post.IMAGE_BLOCKED_REASON);
        assertThat(p.visibleTo(99L, true)).isFalse();
        assertThat(p.visibleTo(AUTHOR, false)).isTrue();
        assertThat(CommunityPostAppService.statusReason(p, AUTHOR)).isEqualTo("图片未通过审核");
        assertThat(CommunityPostAppService.statusReason(p, 99L)).isNull();
        verify(posts).save(p);
    }

    @Test
    void 重复事件幂等_已删除不处理() {
        PostRepository posts = mock(PostRepository.class);
        Post p = published(1L);
        Post deleted = published(2L);
        deleted.deleteBy(AUTHOR);
        when(posts.findByImageUrls(any())).thenReturn(List.of(p, deleted));
        PostImageBlockedListener l = new PostImageBlockedListener(posts);

        l.on(new MediaBlockedEvent(URL, AUTHOR, "POST", 20002));
        l.on(new MediaBlockedEvent(URL, AUTHOR, "POST", 20002));

        verify(posts, times(1)).save(p);
        verify(posts, never()).save(deleted);
        assertThat(deleted.status()).isEqualTo(PostStatus.DELETED);
    }

    @Test
    void 普通被隐藏帖_不带图片原因() {
        Post p = published(1L);
        p.hideByReports();
        assertThat(CommunityPostAppService.statusReason(p, AUTHOR)).isNull();
    }
}

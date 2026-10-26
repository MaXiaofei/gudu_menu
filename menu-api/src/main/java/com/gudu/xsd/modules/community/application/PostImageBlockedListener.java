package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 内容安全（A.6）：图片被判违规 → 含该图的帖子隐藏（仅作者可见，复用举报隐藏的 HIDDEN 状态）并回到人工复核队列，
 * 作者在详情看到「图片未通过审核」。评论没有图片，不涉及。与发布方同事务同步执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostImageBlockedListener {

    private final PostRepository posts;

    @EventListener
    public void on(MediaBlockedEvent event) {
        for (Post p : posts.findByImageUrls(event.urlVariants())) {
            if (p.hideForBlockedImage()) {
                posts.save(p);
                log.warn("帖子图片违规，已隐藏待复核 post={} url={}", p.id(), event.mediaUrl());
            }
        }
    }
}

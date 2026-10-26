package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaRestoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 内容安全复核（F.3）：违规图片经人工复核为误判 → 因该图被隐藏的帖子恢复公开，去掉「图片未通过审核」原因。
 * 与发布方同事务同步执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostImageRestoredListener {

    private final PostRepository posts;

    @EventListener
    public void on(MediaRestoredEvent event) {
        for (Post p : posts.findByImageUrls(event.urlVariants())) {
            if (p.restoreFromBlockedImage()) {
                posts.save(p);
                if (p.hiddenByOthers()) {
                    event.addNotice("帖子 " + p.id() + " 仍有其他处置（举报 / 人工处置），保持隐藏");
                }
                log.info("帖子图片复核恢复 post={} url={} stillHidden={}", p.id(), event.mediaUrl(), p.hiddenByOthers());
            }
        }
    }
}

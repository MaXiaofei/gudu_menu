package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 内容安全（A.6）：图片被判违规 → 清空引用它的成员头像（前端回退默认头像）。
 * 直接改聚合并保存，不走 {@link MemberProfileAppService#update} 的内容安全把关（清空不需要检测，也不能因 452 失败）。
 * 与发布方同事务同步执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarBlockedListener {

    private final MemberProfileRepository repository;

    @EventListener
    public void on(MediaBlockedEvent event) {
        for (MemberProfile p : repository.findByAvatarUrls(event.urlVariants())) {
            p.changeAvatar(null);
            repository.save(p);
            log.warn("头像图片违规，已清空 member={} url={}", p.id(), event.mediaUrl());
        }
    }
}

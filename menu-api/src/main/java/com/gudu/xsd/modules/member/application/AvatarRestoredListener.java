package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.modules.contentsecurity.domain.event.MediaRestoredEvent;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 内容安全复核（F.3）：头像图片经人工复核为误判 → 上传账号在各家庭的档案里，因违规被清空（当前无头像）的头像恢复为该图。
 * 已换过新头像的档案不动。发布方以系统模式同事务调用（跨家庭）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarRestoredListener {

    private final MemberProfileRepository repository;

    @EventListener
    public void on(MediaRestoredEvent event) {
        if (!ContentPurpose.AVATAR.name().equals(event.purpose()) || event.accountId() == null) return;
        for (MemberProfile p : repository.findAllByAccount(event.accountId())) {
            if (p.avatarUrl() != null && !p.avatarUrl().isBlank()) continue;
            p.changeAvatar(event.mediaUrl());
            repository.save(p);
            log.info("头像复核恢复 member={} url={}", p.id(), event.mediaUrl());
        }
    }
}

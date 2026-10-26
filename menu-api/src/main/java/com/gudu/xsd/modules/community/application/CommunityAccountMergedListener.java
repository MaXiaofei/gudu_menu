package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.domain.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并（multi-tenant-family E.7）：厨房圈帖子 / 评论作者、赞 / 收藏、关注、照着做、举报、打卡、申诉、封禁按账号归属，微信账号 W 并入手机号账号 P 后同事务改指 P，
 * 合并后用哪种方式登录都能看到。
 */
@Component
@RequiredArgsConstructor
public class CommunityAccountMergedListener {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final com.gudu.xsd.modules.community.domain.repository.InteractionRepository interactionRepository;
    private final com.gudu.xsd.modules.community.domain.repository.AppealRepository appealRepository;

    @EventListener
    public void on(AccountMerged e) {
        postRepository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
        commentRepository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
        interactionRepository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
        appealRepository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
    }
}

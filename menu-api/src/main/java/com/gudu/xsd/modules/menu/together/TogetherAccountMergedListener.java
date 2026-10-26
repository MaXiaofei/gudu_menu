package com.gudu.xsd.modules.menu.together;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.menu.together.mapper.MenuActivityMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuInviteMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuJoinMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号合并（multi-tenant-family E.7，打回项 2）：聚餐里登录用户的身份是账号 id——
 * menu_join.member_id（加入记录）、menu_invite.created_by（邀请生成人）、menu_activity.member_id（点菜 / 删菜留痕）
 * 由 W 改指 P。W 参与的聚餐可能分属多个家庭，故以系统模式执行、条件只按账号 id。
 * menu_join 有 UNIQUE(menu_id, member_id)：W 与 P 都加入过的同一食集，保留 P 的记录、删除 W 的。
 */
@Component
@RequiredArgsConstructor
public class TogetherAccountMergedListener {

    private final MenuJoinMapper joinMapper;
    private final MenuInviteMapper inviteMapper;
    private final MenuActivityMapper activityMapper;

    @EventListener
    public void on(AccountMerged e) {
        reassign(e.sourceAccountId(), e.targetAccountId());
    }

    void reassign(Long w, Long p) {
        if (w == null || p == null || w.equals(p)) return;
        TenantContextHolder.runAsSystem(() -> {
            List<Long> bothJoined = joinMapper.selectList(new QueryWrapper<MenuJoin>().select("menu_id").eq("member_id", p))
                    .stream().map(MenuJoin::getMenuId).toList();
            if (!bothJoined.isEmpty()) {
                joinMapper.delete(new QueryWrapper<MenuJoin>().eq("member_id", w).in("menu_id", bothJoined));
            }
            joinMapper.update(null, new UpdateWrapper<MenuJoin>().eq("member_id", w).set("member_id", p));
            inviteMapper.update(null, new UpdateWrapper<MenuInvite>().eq("created_by", w).set("created_by", p));
            activityMapper.update(null, new UpdateWrapper<MenuActivity>().eq("member_id", w).set("member_id", p));
        });
    }
}

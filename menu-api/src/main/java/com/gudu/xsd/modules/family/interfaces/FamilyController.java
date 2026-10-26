package com.gudu.xsd.modules.family.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.FamilyDetail;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.FamilySummary;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.InvitationPreview;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.InvitationView;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.SessionFamily;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 家庭（= 租户）接口（design §6）。跨家庭访问 404，角色不足 403。
 * 当前家庭存于登录会话（token 维度），切换 / 加入后立即生效。
 */
@RestController
@RequestMapping("/family")
@RequiredArgsConstructor
@Tag(name = "家庭")
public class FamilyController {

    private final FamilyAppService app;
    private final FamilyQueryApi query;

    @Data
    public static class NameReq {
        private String name;
    }

    @Data
    public static class SwitchReq {
        private Long familyId;
    }

    @Data
    public static class JoinReq {
        private String code;
    }

    @Data
    public static class RoleReq {
        private String role;
    }

    @Data
    public static class TransferReq {
        private Long accountId;
    }

    @Operation(summary = "我加入的家庭 + 当前家庭")
    @GetMapping("/mine")
    public R<Map<String, Object>> mine() {
        long me = StpUtil.getLoginIdAsLong();
        List<FamilySummary> list = query.familiesOf(me);
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("families", list);
        out.put("currentFamilyId", TenantSession.familyId());
        return R.ok(out);
    }

    @Operation(summary = "创建家庭（每账号最多 5 个），创建后切换为当前家庭")
    @PostMapping
    public R<SessionFamily> create(@RequestBody(required = false) NameReq req) {
        long me = StpUtil.getLoginIdAsLong();
        Long id = app.create(me, req == null ? null : req.getName());
        return R.ok(bind(app.switchTo(me, id)));
    }

    @Operation(summary = "当前家庭详情（成员与角色）")
    @GetMapping("/current")
    public R<FamilyDetail> current() {
        Long familyId = TenantSession.familyId();
        if (familyId == null) throw BizException.notFound("尚未选择家庭");
        return R.ok(app.detail(StpUtil.getLoginIdAsLong(), familyId));
    }

    @Operation(summary = "切换当前家庭（非成员 404，当前家庭不变）")
    @PostMapping("/current")
    public R<SessionFamily> switchCurrent(@RequestBody SwitchReq req) {
        if (req == null || req.getFamilyId() == null) throw new BizException("缺少 familyId");
        return R.ok(bind(app.switchTo(StpUtil.getLoginIdAsLong(), req.getFamilyId())));
    }

    @Operation(summary = "改家庭名（OWNER/ADMIN）")
    @PutMapping("/{id}")
    public R<Void> rename(@PathVariable Long id, @RequestBody NameReq req) {
        app.rename(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getName());
        return R.ok(null);
    }

    @Operation(summary = "生成邀请码（OWNER/ADMIN，7 天有效、最多 10 次）")
    @PostMapping("/{id}/invitations")
    public R<InvitationView> invite(@PathVariable Long id) {
        return R.ok(app.invite(StpUtil.getLoginIdAsLong(), id));
    }

    @Operation(summary = "撤销邀请（OWNER/ADMIN）")
    @DeleteMapping("/{id}/invitations/{code}")
    public R<Void> revoke(@PathVariable Long id, @PathVariable String code) {
        app.revokeInvitation(StpUtil.getLoginIdAsLong(), id, code);
        return R.ok(null);
    }

    @Operation(summary = "邀请预览（游客可访问：家庭名 / 邀请人 / 是否可用）")
    @GetMapping("/invitations/{code}")
    public R<InvitationPreview> preview(@PathVariable String code) {
        return R.ok(app.preview(code));
    }

    @Operation(summary = "凭邀请码加入，加入后切换为当前家庭")
    @PostMapping("/join")
    public R<SessionFamily> join(@RequestBody JoinReq req) {
        return R.ok(bind(app.join(StpUtil.getLoginIdAsLong(), req == null ? null : req.getCode())));
    }

    @Operation(summary = "调整成员角色（OWNER；ADMIN / MEMBER）")
    @PutMapping("/{id}/members/{accountId}/role")
    public R<Void> changeRole(@PathVariable Long id, @PathVariable Long accountId, @RequestBody RoleReq req) {
        app.changeRole(StpUtil.getLoginIdAsLong(), id, accountId, req == null ? null : req.getRole());
        return R.ok(null);
    }

    @Operation(summary = "移除成员（OWNER 任意；ADMIN 仅 MEMBER）")
    @DeleteMapping("/{id}/members/{accountId}")
    public R<Void> remove(@PathVariable Long id, @PathVariable Long accountId) {
        app.remove(StpUtil.getLoginIdAsLong(), id, accountId);
        return R.ok(null);
    }

    @Operation(summary = "退出家庭（OWNER 须先转让或解散）")
    @PostMapping("/{id}/leave")
    public R<Void> leave(@PathVariable Long id) {
        app.leave(StpUtil.getLoginIdAsLong(), id);
        clearIfCurrent(id);
        return R.ok(null);
    }

    @Operation(summary = "转让主理人（OWNER；原主理人降为 ADMIN）")
    @PostMapping("/{id}/transfer")
    public R<Void> transfer(@PathVariable Long id, @RequestBody TransferReq req) {
        app.transfer(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getAccountId());
        return R.ok(null);
    }

    @Operation(summary = "解散家庭（OWNER；家庭数据保留但不可再访问）")
    @DeleteMapping("/{id}")
    public R<Void> dissolve(@PathVariable Long id) {
        app.dissolve(StpUtil.getLoginIdAsLong(), id);
        clearIfCurrent(id);
        return R.ok(null);
    }

    private static SessionFamily bind(SessionFamily sf) {
        TenantSession.bind(sf.familyId(), sf.profileId());
        return sf;
    }

    /** 离开 / 解散的是当前家庭 → 清会话，下个请求自动落到剩余家庭（或新建「我的家」）。 */
    private static void clearIfCurrent(Long familyId) {
        if (familyId.equals(TenantSession.familyId())) TenantSession.clearFamily();
    }
}

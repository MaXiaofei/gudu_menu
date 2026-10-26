package com.gudu.xsd.modules.identity.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.modules.auth.AuthService;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.identity.application.AccountAppService;
import com.gudu.xsd.modules.identity.application.AccountMergeAppService;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import com.gudu.xsd.modules.identity.domain.AccountWechatConflictException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 账号接口（design §6 /account/**）：资料、改密、注销、微信账号并入手机号账号、超管代建手机号账号。 */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
@Tag(name = "账号")
public class AccountController {

    private final AccountAppService accounts;
    private final AccountMergeAppService mergeApp;
    private final FamilyQueryApi familyQuery;
    private final FamilyAppService familyApp;
    private final AuthService authService;

    @Data
    public static class ProfileReq {
        private String nickname;
        private String avatarUrl;
    }

    @Data
    public static class PasswordReq {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class MergeReq {
        private String phone;
        private String password;
    }

    @Data
    public static class CreateReq {
        private String phone;
        private String password;
        private String nickname;
        /** true：同时加入操作人的当前家庭（MEMBER）；否则新账号自动建「我的家」。 */
        private Boolean joinCurrentFamily;
    }

    @Operation(summary = "我的账号 + 我加入的家庭 + 当前家庭")
    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        long me = StpUtil.getLoginIdAsLong();
        Map<String, Object> out = new HashMap<>();
        out.put("account", accounts.me(me));
        out.put("families", familyQuery.familiesOf(me));
        out.put("currentFamilyId", TenantSession.familyId());
        out.put("currentMemberId", TenantSession.currentMemberId());
        return R.ok(out);
    }

    @Operation(summary = "改昵称 / 头像（头像只收本站上传地址，空串清除）")
    @PutMapping("/me")
    public R<AccountView> update(@RequestBody ProfileReq req) {
        return R.ok(accounts.updateProfile(StpUtil.getLoginIdAsLong(), req.getNickname(), req.getAvatarUrl()));
    }

    @Operation(summary = "改密码（已有密码须校验旧密码）")
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody PasswordReq req) {
        accounts.changePassword(StpUtil.getLoginIdAsLong(), req.getOldPassword(), req.getNewPassword());
        return R.ok(null);
    }

    /**
     * 注销账号：是有其他成员的家庭的主理人 → 拒绝（先转让或解散）；只剩自己的家庭随之解散；
     * 其余家庭退出；手机号 / 微信 / 昵称 / 头像匿名化，全部登录态失效。
     */
    @Operation(summary = "注销账号（应用商店 / 微信审核要求）")
    @DeleteMapping("/me")
    public R<Void> deactivate() {
        long me = StpUtil.getLoginIdAsLong();
        accounts.deactivate(me);
        StpUtil.logout(me);
        return R.ok(null);
    }

    /**
     * 微信账号并入手机号账号（以手机号账号为主）：密码校验通过后，微信身份与家庭 / 个人数据并入手机号账号，
     * 当前微信账号失效（全部 token 下线），返回手机号账号的新 token；当前家庭沿用合并前的家庭。
     */
    @Operation(summary = "微信账号并入手机号账号 {phone, password} → 新 token")
    @PostMapping("/merge-phone")
    public R<Map<String, Object>> mergePhone(@RequestBody MergeReq req) {
        if (req == null || req.getPhone() == null || req.getPassword() == null) {
            throw new BizException("请输入手机号和密码");
        }
        long w = StpUtil.getLoginIdAsLong();
        Long keepFamily = TenantSession.familyId();
        Long target;
        try {
            target = mergeApp.mergeWechatIntoPhone(w, req.getPhone(), req.getPassword());
        } catch (AccountWechatConflictException e) {
            throw new BizException(BizException.CONFLICT, e.getMessage() + "（" + AccountWechatConflictException.CODE + "）");
        }
        StpUtil.logout(w);
        return R.ok(authService.establish(accounts.me(target), null, keepFamily));
    }

    /** 平台超管代建手机号账号（V1 无短信）。可选直接加入超管当前家庭。 */
    @Operation(summary = "【超管】创建手机号账号")
    @PostMapping
    @RequireAdmin
    public R<Long> create(@RequestBody CreateReq req) {
        Long current = TenantSession.familyId();
        boolean join = Boolean.TRUE.equals(req.getJoinCurrentFamily()) && current != null;
        return R.ok(familyApp.registerPhoneAccount(req.getPhone(), req.getPassword(), req.getNickname(),
                join ? current : null));
    }
}

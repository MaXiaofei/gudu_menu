package com.gudu.xsd.modules.menu.together;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 食集聚餐接口（V45，设计文档 §8）。
 *
 * <p>身份解析：登录用户（Sa-Token）→ memberId；H5 访客 → 请求头 {@code X-Guest-Key}。
 * 邀请链路：APP 生成（口令+二维码+链接同效）→ H5（together.html?token=）输昵称 join → guestKey 存 localStorage。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "聚餐")
public class TogetherController {

    public static final String HEADER_GUEST_KEY = "X-Guest-Key";

    private final TogetherService svc;
    private final HttpServletRequest request;

    /** 生成/刷新邀请（登录用户，一食集一邀请）。 */
    @PostMapping("/menu/{id}/invite")
    public R<TogetherService.InviteVO> invite(@PathVariable Long id) {
        return R.ok(svc.invite(id, currentMemberId(true)));
    }

    /** 邀请信息（免身份，H5 进入页校验 token）。 */
    @GetMapping("/invite/{token}")
    public R<TogetherService.InviteInfoVO> inviteInfo(@PathVariable String token) {
        return R.ok(svc.inviteInfo(token));
    }

    /** 按 6 位口令查邀请（免身份，H5 无链接场景：朋友在页面输入口令进入）。 */
    @GetMapping("/invite/code/{code}")
    public R<TogetherService.InviteInfoVO> inviteInfoByCode(@PathVariable String code) {
        return R.ok(svc.inviteInfoByCode(code));
    }

    /** 加入聚餐：登录用户按账号 upsert；H5 访客按昵称建/复用 guest_key。 */
    @PostMapping("/invite/{token}/join")
    public R<TogetherService.JoinVO> join(@PathVariable String token, @RequestBody JoinReq req) {
        Long memberId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(svc.join(token, req == null ? null : req.getNickname(), memberId));
    }

    /** 聚餐清单（轮询）：成员 + 菜 + 动态 + 邀请；同时更新本人心跳。 */
    @GetMapping("/menu/{id}/together")
    public R<TogetherService.TogetherVO> together(@PathVariable Long id) {
        return R.ok(svc.together(id, identity()));
    }

    /** 朋友加菜：dishId（菜谱）或 customName（自由输入）二选一，可带备注。 */
    @PostMapping("/menu/{id}/together/items")
    public R<Long> addItem(@PathVariable Long id, @RequestBody AddItemReq req) {
        return R.ok(svc.addItem(id, identity(),
                req == null ? null : req.getDishId(),
                req == null ? null : req.getCustomName(),
                req == null ? null : req.getNote()));
    }

    /** 删菜（已加入成员可删任意菜，留痕谁删的）。 */
    @DeleteMapping("/menu/{id}/together/items/{menuDishId}")
    public R<?> removeItem(@PathVariable Long id, @PathVariable Long menuDishId) {
        svc.removeItem(id, identity(), menuDishId);
        return R.ok(null);
    }

    /** 修改昵称（已加入成员；H5 顶部昵称 ✎ 铅笔编辑）。 */
    @PutMapping("/menu/{id}/together/nickname")
    public R<?> updateNickname(@PathVariable Long id, @RequestBody NicknameReq req) {
        svc.updateNickname(id, identity(), req == null ? null : req.getNickname());
        return R.ok(null);
    }

    // ===================== 请求体 / 身份 =====================

    @Data
    public static class JoinReq {
        private String nickname;
    }

    @Data
    public static class NicknameReq {
        private String nickname;
    }

    @Data
    public static class AddItemReq {
        private Long dishId;
        private String customName;
        private String note;
    }

    /** 登录用户 → member；否则读 X-Guest-Key（H5 访客）。 */
    private TogetherService.Identity identity() {
        if (StpUtil.isLogin()) {
            return TogetherService.Identity.member(StpUtil.getLoginIdAsLong());
        }
        String guestKey = request.getHeader(HEADER_GUEST_KEY);
        if (guestKey != null && !guestKey.isBlank()) {
            return TogetherService.Identity.guest(guestKey);
        }
        throw new BizException("请先加入聚餐");
    }

    private Long currentMemberId(boolean required) {
        if (!StpUtil.isLogin()) {
            if (required) throw new BizException("请先登录");
            return null;
        }
        return StpUtil.getLoginIdAsLong();
    }
}

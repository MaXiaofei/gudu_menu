package com.gudu.xsd.modules.backoffice.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.backoffice.application.BackofficeAppService;
import com.gudu.xsd.modules.backoffice.application.dto.BackofficeViews;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;
import com.gudu.xsd.modules.privacy.application.ConsentAppService.ConsentHistoryView;
import com.gudu.xsd.modules.contentsecurity.application.dto.ViolationViews;
import com.gudu.xsd.modules.identity.application.AccountBanAppService;
import com.gudu.xsd.modules.identity.application.AccountDeactivationAppService;
import com.gudu.xsd.modules.identity.application.dto.DeactivationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

/**
 * 运营后台（mini-release-compliance F.1–F.4）：平台运营 / 超管可用（{@link RequireOperator}，其余 403）。
 * 只展示账号元数据、注销处理、内容安全违规与同意记录，不提供任何家庭私有数据（菜单 / 余量 / 采购 / 成员档案）。
 * 封禁、解封、恢复、确认违规、重试注销均写操作日志。
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@RequireOperator
@Tag(name = "运营后台")
public class BackofficeController {

    private final BackofficeAppService app;

    @Data
    public static class ReasonReq {
        private String reason;
    }

    // ---------------------------------------------------------------- F.1 账号

    @Operation(summary = "账号列表：昵称/ID、openid 尾号（≥4 位）、家庭、状态、仅封禁")
    @GetMapping("/accounts")
    public R<BackofficeViews.Page<BackofficeViews.AccountItem>> accounts(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String openidTail,
            @RequestParam(required = false) Long familyId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean bannedOnly,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(app.accounts(new AccountSearch(keyword, openidTail, familyId, status, bannedOnly), pageNum,
                pageSize));
    }

    @Operation(summary = "封禁账号 {reason}：立即下线全部登录态，之后登录与写接口返回 423")
    @PostMapping("/accounts/{id}/ban")
    public R<AccountBanAppService.BanView> ban(@PathVariable Long id, @RequestBody ReasonReq req) {
        AccountBanAppService.BanView v = app.ban(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason());
        StpUtil.logout(id);
        return R.ok(v);
    }

    @Operation(summary = "解封账号 {reason}")
    @PostMapping("/accounts/{id}/unban")
    public R<Boolean> unban(@PathVariable Long id, @RequestBody ReasonReq req) {
        return R.ok(app.unban(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason()));
    }

    @Operation(summary = "账号的协议同意记录（版本、同意 / 撤回、时间、端）")
    @GetMapping("/accounts/{id}/consents")
    public R<List<ConsentHistoryView>> consents(@PathVariable Long id) {
        return R.ok(app.consents(id));
    }

    // ---------------------------------------------------------------- F.2 注销

    @Operation(summary = "注销处理记录：status=PENDING/DONE/FAILED，overdue=true 只看超 15 个工作日未完成")
    @GetMapping("/deactivations")
    public R<AccountDeactivationAppService.Page> deactivations(@RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "false") boolean overdue,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(app.deactivations(status, overdue, pageNum, pageSize));
    }

    @Operation(summary = "重试注销后续匿名化（仅未完成的记录）")
    @PostMapping("/deactivations/{id}/retry")
    public R<DeactivationView> retry(@PathVariable Long id) {
        return R.ok(app.retryDeactivation(StpUtil.getLoginIdAsLong(), id));
    }

    // ---------------------------------------------------------------- F.3 违规复核

    @Operation(summary = "图片违规：review=PENDING/CONFIRMED/RESTORED，purpose，accountId")
    @GetMapping("/content-violations/media")
    public R<ViolationViews.Page<ViolationViews.MediaViolation>> media(
            @RequestParam(required = false) String review, @RequestParam(required = false) String purpose,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(app.mediaViolations(review, purpose, accountId, pageNum, pageSize));
    }

    @Operation(summary = "违规图片原图（隔离区，复核查看；原地址已是占位图）→ data URL；不在隔离区返回 null")
    @GetMapping("/content-violations/media/{id}/image")
    public R<String> mediaImage(@PathVariable Long id) {
        return R.ok(app.quarantinedImage(id)
                .map(b -> "data:" + imageType(b) + ";base64," + Base64.getEncoder().encodeToString(b))
                .orElse(null));
    }

    /** 按文件头识别图片类型（隔离文件不带 Content-Type）。 */
    static String imageType(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length > 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "image/gif";
        if (b.length > 11 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return "image/png";
    }

    @Operation(summary = "恢复违规图片（误判）{reason}：还原隔离文件、恢复头像 / 帖子")
    @PostMapping("/content-violations/media/{id}/restore")
    public R<ViolationViews.RestoreResult> restore(@PathVariable Long id, @RequestBody ReasonReq req) {
        return R.ok(app.restoreMedia(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason()));
    }

    @Operation(summary = "确认图片违规 {reason?}")
    @PostMapping("/content-violations/media/{id}/confirm")
    public R<ViolationViews.MediaViolation> confirmMedia(@PathVariable Long id,
                                                         @RequestBody(required = false) ReasonReq req) {
        return R.ok(app.confirmMedia(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason()));
    }

    @Operation(summary = "文本违规：confirmed=true/false，purpose，accountId")
    @GetMapping("/content-violations/text")
    public R<ViolationViews.Page<ViolationViews.TextViolation>> text(
            @RequestParam(required = false) Boolean confirmed, @RequestParam(required = false) String purpose,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(app.textViolations(confirmed, purpose, accountId, pageNum, pageSize));
    }

    @Operation(summary = "确认文本违规 {reason?}")
    @PostMapping("/content-violations/text/{id}/confirm")
    public R<ViolationViews.TextViolation> confirmText(@PathVariable Long id,
                                                       @RequestBody(required = false) ReasonReq req) {
        return R.ok(app.confirmText(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason()));
    }

    // ---------------------------------------------------------------- 操作日志

    @Operation(summary = "运营操作日志：targetType=ACCOUNT/MEDIA_CHECK/TEXT_VIOLATION/DEACTIVATION，targetId")
    @GetMapping("/operation-logs")
    public R<BackofficeViews.Page<BackofficeViews.OperationLogItem>> operationLogs(
            @RequestParam(required = false) String targetType, @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(app.operationLogs(targetType, targetId, pageNum, pageSize));
    }
}

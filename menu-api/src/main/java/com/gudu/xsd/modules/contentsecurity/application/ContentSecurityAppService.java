package com.gudu.xsd.modules.contentsecurity.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.domain.UploadedImageUrl;
import com.gudu.xsd.config.WxProperties;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckResult;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurityUnavailableException;
import com.gudu.xsd.modules.contentsecurity.domain.port.OpenidResolver;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.domain.service.RuleContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.service.WxPushSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 内容安全用例（A.6）：文本同步检测、图片异步检测与回调定论。各上下文只调本服务，不直接碰端口实现。
 *
 * <p><b>路由</b>：规则先行（禁发词，本地、零成本，命中即拦）→ 开关打开且账号有 openid 时再走微信；
 * 开关关闭或无 openid（App / 网页账号）只用规则。
 *
 * <p><b>失败策略</b>（微信不可用时）：{@link ContentPurpose#failClosed()} 为 true（昵称 / 头像）拒绝并提示稍后再试；
 * 其余（AI 对话输入输出、反馈、社区）降级到规则结论。
 */
@Slf4j
@Service
public class ContentSecurityAppService {

    /** 各场景对用户的拒绝提示（不暴露命中标签与原因）。 */
    static final String MSG_NICKNAME = "昵称未通过内容安全检测，换一个试试";
    static final String MSG_AVATAR = "头像未通过内容安全检测，换一张试试";
    static final String MSG_DEFAULT = "内容未通过内容安全检测，请修改后再试";
    static final String MSG_UNAVAILABLE = "内容安全检测暂时不可用，请稍后再试";
    /** AI 对话：输入违规时的回复；输出违规时替换成的安全提示。 */
    public static final String AGENT_INPUT_BLOCKED = "这条消息没有通过内容安全检测，换个说法再问我吧。";
    public static final String AGENT_REPLY_REPLACED = "这个回答没有通过内容安全检测，已隐藏。换个做饭相关的问题再问问我吧。";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ContentSecurity wechat;
    private final ContentSecurity rule = new RuleContentSecurity();
    private final OpenidResolver openids;
    private final MediaCheckRepository mediaChecks;
    private final UploadQuarantine quarantine;
    private final DomainEventPublisher events;
    private final Executor executor;
    private final WxProperties wxProps;
    private final Clock clock;
    private final boolean wxEnabled;
    private final String mediaBaseUrl;

    public ContentSecurityAppService(ContentSecurity wechat, OpenidResolver openids, MediaCheckRepository mediaChecks,
                                     UploadQuarantine quarantine, DomainEventPublisher events,
                                     @Qualifier("contentSecurityExecutor") Executor executor, WxProperties wxProps,
                                     Clock clock,
                                     @Value("${gudu.content-security.wx-enabled:false}") boolean wxEnabled,
                                     @Value("${gudu.content-security.media-base-url:}") String mediaBaseUrl) {
        this.wechat = wechat;
        this.openids = openids;
        this.mediaChecks = mediaChecks;
        this.quarantine = quarantine;
        this.events = events;
        this.executor = executor;
        this.wxProps = wxProps;
        this.clock = clock;
        this.wxEnabled = wxEnabled;
        this.mediaBaseUrl = mediaBaseUrl == null ? "" : mediaBaseUrl.trim().replaceAll("/+$", "");
    }

    // ================================================================ 文本

    /** 检测文本，返回结论（不抛业务异常）。微信不可用且用途失败即拒绝时返回 {@link TextVerdict#unavailable}。 */
    public TextVerdict checkText(Long accountId, ContentPurpose purpose, String text) {
        if (text == null || text.isBlank()) return TextVerdict.pass(TextVerdict.Source.RULE);
        TextVerdict byRule = rule.checkText(new TextCheck(text, purpose.scene(), null));
        if (byRule.blocks(purpose)) return record(accountId, purpose, byRule);
        Optional<String> openid = wechatOpenid(accountId);
        if (openid.isEmpty()) return byRule;
        try {
            return record(accountId, purpose, wechat.checkText(new TextCheck(text, purpose.scene(), openid.get())));
        } catch (ContentSecurityUnavailableException e) {
            log.warn("微信内容安全不可用 purpose={} account={}: {}", purpose, accountId, e.getMessage());
            return purpose.failClosed() ? TextVerdict.unavailable(e.getMessage())
                    : byRule.withSource(TextVerdict.Source.RULE_FALLBACK);
        }
    }

    /** 检测文本，不通过抛 {@link BizException}：违规 451、检测不可用（仅失败即拒绝的用途）452。 */
    public void assertText(Long accountId, ContentPurpose purpose, String text) {
        TextVerdict v = checkText(accountId, purpose, text);
        if (v.isUnavailable()) throw BizException.contentCheckUnavailable(MSG_UNAVAILABLE);
        if (v.blocks(purpose)) throw BizException.contentBlocked(messageOf(purpose));
    }

    /** AI 对话输入（发模型前）：违规返回给用户的提示；微信不可用降级到规则。 */
    public Optional<String> agentInputViolation(Long accountId, String text) {
        return checkText(accountId, ContentPurpose.AGENT_INPUT, text).blocks(ContentPurpose.AGENT_INPUT)
                ? Optional.of(AGENT_INPUT_BLOCKED) : Optional.empty();
    }

    /** AI 对话输出（放出前）：违规替换为安全提示；微信不可用降级到规则。 */
    public String reviewAgentReply(Long accountId, String reply) {
        if (reply == null || reply.isBlank()) return reply;
        return checkText(accountId, ContentPurpose.AGENT_OUTPUT, reply).blocks(ContentPurpose.AGENT_OUTPUT)
                ? AGENT_REPLY_REPLACED : reply;
    }

    // ================================================================ 图片

    /** 上传后异步提交检测（不阻塞上传、不抛异常）。 */
    public void submitImageLater(Long accountId, String uploadedUrl, ContentPurpose purpose) {
        if (!wxEnabled || !UploadedImageUrl.isValid(uploadedUrl)) return;
        try {
            executor.execute(() -> {
                try {
                    submitImage(accountId, uploadedUrl, purpose);
                } catch (RuntimeException e) {
                    log.warn("提交图片检测失败 url={}: {}", uploadedUrl, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            // 队列满：不影响上传；头像保存时会同步补提交
            log.warn("图片检测队列已满，跳过 url={}", uploadedUrl);
        }
    }

    /**
     * 同步提交一次图片检测并留痕。
     *
     * @return 检测记录；未走微信（开关关闭 / 无 openid）返回 empty
     */
    public Optional<MediaCheck> submitImage(Long accountId, String uploadedUrl, ContentPurpose purpose) {
        Optional<String> openid = wechatOpenid(accountId);
        if (openid.isEmpty() || !UploadedImageUrl.isValid(uploadedUrl)) return Optional.empty();
        LocalDateTime now = LocalDateTime.now(clock);
        MediaCheck check;
        if (mediaBaseUrl.isEmpty()) {
            log.warn("未配置 gudu.content-security.media-base-url，图片无法提交微信检测 url={}", uploadedUrl);
            check = MediaCheck.submitFailed(uploadedUrl, accountId, purpose, now);
        } else {
            try {
                String traceId = wechat.submitMedia(new MediaCheckRequest(mediaBaseUrl + uploadedUrl, purpose.scene(),
                        openid.get()));
                check = MediaCheck.submitted(traceId, uploadedUrl, accountId, purpose, now);
            } catch (ContentSecurityUnavailableException e) {
                log.warn("提交微信图片检测不可用 url={}: {}", uploadedUrl, e.getMessage());
                check = MediaCheck.submitFailed(uploadedUrl, accountId, purpose, now);
            }
        }
        mediaChecks.save(check);
        return Optional.of(check);
    }

    /**
     * 图片用于失败即拒绝的场景（头像）前的把关：已判违规 → 451；需要走微信但从未成功提交 → 同步补提交，仍失败 → 452。
     * 已提交待回调的放行（回调判违规后文件隔离，头像随即不可见）。未走微信的账号（无 openid / 开关关闭）放行。
     */
    public void assertImageUsable(Long accountId, String uploadedUrl, ContentPurpose purpose) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return; // 地址合法性由各聚合自己校验
        Optional<MediaCheck> latest = mediaChecks.latestByUrl(uploadedUrl);
        if (latest.filter(MediaCheck::blocked).isPresent()) throw BizException.contentBlocked(messageOf(purpose));
        if (wechatOpenid(accountId).isEmpty()) return;
        if (latest.isPresent() && !latest.get().needsResubmit()) return;
        Optional<MediaCheck> submitted = submitImage(accountId, uploadedUrl, purpose);
        if (submitted.isPresent() && submitted.get().needsResubmit() && purpose.failClosed()) {
            throw BizException.contentCheckUnavailable(MSG_UNAVAILABLE);
        }
    }

    // ================================================================ 回调

    /**
     * 微信消息推送（明文模式 + JSON）：校验签名后处理 wxa_media_check 事件，其它事件忽略。
     *
     * @return false = 签名不通过（调用方拒绝）
     */
    public boolean handlePush(String signature, String timestamp, String nonce, String body) {
        if (!WxPushSignature.verify(wxProps.getToken(), timestamp, nonce, signature, clock.instant().getEpochSecond())) {
            log.warn("微信消息推送签名校验失败 timestamp={}", timestamp);
            return false;
        }
        parseMediaCheck(body).ifPresent(this::onMediaCheckResult);
        return true;
    }

    /**
     * 图片检测结果定论：违规则发布 MediaBlockedEvent（同事务同步：头像清空、帖子隐藏），订阅方都成功后再隔离文件、
     * 原地址换占位图——订阅方失败则整体回滚、文件不动，回调返回错误由微信重试。重复推送幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onMediaCheckResult(MediaCheckResult result) {
        String appid = wxProps.getAppid();
        if (appid != null && !appid.isBlank() && result.appid() != null && !appid.equals(result.appid())) {
            log.warn("忽略其它小程序的检测推送 appid={}", result.appid());
            return;
        }
        Optional<MediaCheck> found = mediaChecks.findByTraceId(result.traceId());
        if (found.isEmpty()) {
            log.info("未知的图片检测 trace_id={}", result.traceId());
            return;
        }
        MediaCheck check = found.get();
        boolean blocked = check.resolve(result);
        mediaChecks.save(check);
        events.publishFrom(check);
        if (blocked) {
            boolean moved = quarantine.quarantine(check.mediaUrl());
            log.warn("图片违规已隔离 url={} label={} moved={}", check.mediaUrl(), check.label(), moved);
        }
    }

    static Optional<MediaCheckResult> parseMediaCheck(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        JsonNode n;
        try {
            n = JSON.readTree(body);
        } catch (Exception e) {
            log.warn("微信消息推送不是 JSON（请在公众平台选择 JSON 数据格式、明文模式）");
            return Optional.empty();
        }
        if (n == null || !"wxa_media_check".equals(n.path("Event").asText(""))) return Optional.empty();
        String traceId = n.path("trace_id").asText("");
        if (traceId.isEmpty()) return Optional.empty();
        JsonNode result = n.path("result");
        return Optional.of(new MediaCheckResult(n.path("appid").asText(null), traceId,
                Suggest.ofWx(result.path("suggest").asText(null)), result.path("label").asInt(0),
                n.path("errcode").asInt(0)));
    }

    // ================================================================ 内部

    /** 走微信需要：开关打开 + 账号有 openid。 */
    private Optional<String> wechatOpenid(Long accountId) {
        if (!wxEnabled || accountId == null) return Optional.empty();
        return openids.openidOf(accountId).filter(s -> !s.isBlank());
    }

    private static String messageOf(ContentPurpose purpose) {
        return switch (purpose) {
            case NICKNAME -> MSG_NICKNAME;
            case AVATAR -> MSG_AVATAR;
            default -> MSG_DEFAULT;
        };
    }

    private static TextVerdict record(Long accountId, ContentPurpose purpose, TextVerdict v) {
        if (v.suggest() != Suggest.PASS) {
            log.info("内容安全 purpose={} account={} suggest={} label={} source={} reason={} trace={}", purpose,
                    accountId, v.suggest(), v.label(), v.source(), v.reason(), v.traceId());
        }
        return v;
    }
}

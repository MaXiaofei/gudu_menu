package com.gudu.xsd.common.upload;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上传文件短时签名 URL（A.3）：{@code /gudu/uploads/original/20261026/xxx.jpg?e=<过期秒>&s=<签名>}。
 *
 * <ul>
 *   <li>签名 = base64url(HMAC-SHA256(secret, 规范路径 + "\n" + e)) 取前 16 字节；
 *       规范路径是 {@code /uploads/} 之后的相对路径，且 {@code thumbnail/} 归一为 {@code original/}——
 *       前端把原图地址里的 /original/ 换成 /thumbnail/ 取缩略图（utils/image.ts、Flutter ImageHelper、admin image.ts），
 *       同一签名对原图与缩略图都有效，前端无需改拼图规则；</li>
 *   <li>过期时间 = now + ttl 向上取整到整点（bucket），同一小时内同一文件签出的 URL 不变，浏览器/微信图片缓存可命中；</li>
 *   <li>出参统一由 {@link UploadUrlJacksonModule} 在 HTTP 序列化时签名，入参在反序列化时验签并剥掉签名（库里只存规范地址）；</li>
 *   <li>{@code /uploads/**} 访问由 UploadSignatureInterceptor 用 {@link #verifyRequest} 校验。</li>
 * </ul>
 *
 * <p>secret 为空 = 不签名（dev/test 未配置时 URL 原样输出）；enforce=false 时不校验访问与入参签名。
 */
public final class UploadUrlSigner {

    public static final String PARAM_EXPIRES = "e";
    public static final String PARAM_SIGNATURE = "s";

    private static final String THUMBNAIL = "thumbnail/";
    private static final String ORIGINAL = "original/";
    private static final int SIG_BYTES = 16;
    private static final long MAX_BUCKET_SECONDS = 3600;
    private static final int MIN_SECRET_LENGTH = 16;

    /**
     * 本站上传地址：相对路径，或本站域名（imxf.cloud / staging.imxf.cloud，与 SiteImage 一致）的绝对地址；
     * 可带 /gudu 前缀与本签名参数。外站域名一律不签——否则外链 /uploads/… 会被签出对本站同路径有效的签名。
     */
    private static final Pattern UPLOAD_URL = Pattern.compile(
            "^((?:https://(?:staging\\.)?imxf\\.cloud)?(?:/gudu)?/uploads/)([A-Za-z0-9_\\-./]+)"
                    + "(?:\\?" + PARAM_EXPIRES + "=(\\d{1,12})&" + PARAM_SIGNATURE + "=([A-Za-z0-9_\\-]{1,64}))?$");

    private final SecretKeySpec key;
    private final long ttlSeconds;
    private final long bucketSeconds;
    private final long inputGraceSeconds;
    private final boolean enforce;
    private final Clock clock;

    /**
     * @param secret     HMAC 密钥；为空则不签名（此时 enforce 必须为 false）
     * @param ttl        签名有效期（实际过期再向上取整到整点）
     * @param inputGrace 入参验签时对已过期签名的宽限（用户停留在编辑页较久后提交仍可保存）
     * @param enforce    是否校验访问与入参签名（dev/test 可关）
     */
    public UploadUrlSigner(String secret, Duration ttl, Duration inputGrace, boolean enforce, Clock clock) {
        boolean hasSecret = secret != null && !secret.isBlank();
        if (enforce && (!hasSecret || secret.trim().length() < MIN_SECRET_LENGTH)) {
            throw new IllegalStateException("gudu.upload-sign.enabled=true 时必须配置 gudu.upload-sign.secret（GUDU_UPLOAD_SIGN_SECRET，至少 "
                    + MIN_SECRET_LENGTH + " 位）");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("gudu.upload-sign.ttl 必须为正");
        }
        this.key = hasSecret ? new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256") : null;
        this.ttlSeconds = ttl.toSeconds();
        this.bucketSeconds = Math.max(1, Math.min(MAX_BUCKET_SECONDS, ttlSeconds));
        this.inputGraceSeconds = inputGrace == null ? 0 : Math.max(0, inputGrace.toSeconds());
        this.enforce = enforce;
        this.clock = clock;
    }

    /** 是否会给出参签名（配置了密钥）。 */
    public boolean signing() {
        return key != null;
    }

    /** 是否校验访问 / 入参签名。 */
    public boolean enforcing() {
        return enforce;
    }

    /** 是否本站上传地址（含已签名形式）。 */
    public static boolean isUploadUrl(String value) {
        return value != null && value.contains("/uploads/") && parse(value) != null;
    }

    // ================================================================ 出参：签名

    /** 单个地址签名（已有签名会被替换）；非本站上传地址或未配置密钥原样返回。 */
    public String sign(String url) {
        return sign(url, ttlSeconds);
    }

    /** 指定有效期签名（如提交给微信内容安全拉图）。 */
    public String sign(String url, Duration ttl) {
        return sign(url, ttl.toSeconds());
    }

    private String sign(String url, long ttl) {
        if (key == null || url == null) return url;
        Parsed p = parse(url);
        if (p == null) return url;
        long now = clock.instant().getEpochSecond();
        long expires = ceilToBucket(now + ttl);
        return p.prefix + p.rel + "?" + PARAM_EXPIRES + "=" + expires + "&" + PARAM_SIGNATURE + "=" + signature(p.rel, expires);
    }

    /** 字段值签名：单个地址或逗号分隔的多图字段（如帖子/菜谱 images）。 */
    public String signAll(String value) {
        if (key == null || value == null || !value.contains("/uploads/")) return value;
        if (value.indexOf(',') < 0) return sign(value);
        String[] parts = value.split(",", -1);
        for (String part : parts) {
            if (!part.isEmpty() && parse(part) == null) return value; // 不是纯地址列表（普通文本），不动
        }
        StringBuilder sb = new StringBuilder(value.length() + parts.length * 40);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(parts[i].isEmpty() ? parts[i] : sign(parts[i]));
        }
        return sb.toString();
    }

    // ================================================================ 入参：验签并还原规范地址

    /**
     * 客户端提交的地址 → 去掉签名的规范地址（入库用）。
     * 校验开启时，本站上传地址必须带本服务签发的有效签名（允许过期 inputGrace 内），
     * 证明地址确实是服务端下发给该客户端的——防止拿猜出来的旧时间戳文件名「引用」他人图片再由接口签名回显。
     *
     * @throws UploadUrlRejectedException 校验开启且签名缺失/无效
     */
    public String accept(String url) {
        if (url == null) return null;
        Parsed p = parse(url);
        if (p == null) return url;
        String canonical = p.prefix + p.rel;
        if (!enforce) return canonical;
        if (p.expires == null || !validSignature(p.rel, p.expires, p.signature)) {
            throw new UploadUrlRejectedException();
        }
        long now = clock.instant().getEpochSecond();
        if (Long.parseLong(p.expires) + inputGraceSeconds < now) throw new UploadUrlRejectedException();
        return canonical;
    }

    /** 字段值还原：单个地址或逗号分隔的多图字段。 */
    public String acceptAll(String value) {
        if (value == null || !value.contains("/uploads/")) return value;
        if (value.indexOf(',') < 0) return accept(value);
        String[] parts = value.split(",", -1);
        for (String part : parts) {
            if (!part.isEmpty() && parse(part) == null) return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(parts[i].isEmpty() ? parts[i] : accept(parts[i]));
        }
        return sb.toString();
    }

    /** 去掉签名参数（不校验），用于日志/比较。 */
    public static String strip(String url) {
        Parsed p = url == null ? null : parse(url);
        return p == null ? url : p.prefix + p.rel;
    }

    // ================================================================ 访问校验

    /**
     * 校验 {@code /uploads/**} 请求。
     *
     * @param relPath   {@code /uploads/} 之后的相对路径
     * @param expires   参数 e
     * @param signature 参数 s
     */
    public boolean verifyRequest(String relPath, String expires, String signature) {
        if (!enforce) return true;
        if (relPath == null || relPath.isEmpty() || relPath.contains("..") || expires == null || signature == null
                || !expires.matches("\\d{1,12}")) {
            return false;
        }
        if (Long.parseLong(expires) < clock.instant().getEpochSecond()) return false;
        return validSignature(relPath, expires, signature);
    }

    // ================================================================ 内部

    private boolean validSignature(String rel, String expires, String signature) {
        if (key == null || signature == null) return false;
        byte[] expected = signature(rel, Long.parseLong(expires)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String signature(String rel, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] full = mac.doFinal((canonical(rel) + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, SIG_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    /** 缩略图与原图共用签名：thumbnail/ → original/。 */
    static String canonical(String rel) {
        return rel.startsWith(THUMBNAIL) ? ORIGINAL + rel.substring(THUMBNAIL.length()) : rel;
    }

    private long ceilToBucket(long epochSecond) {
        long r = epochSecond % bucketSeconds;
        return r == 0 ? epochSecond : epochSecond + (bucketSeconds - r);
    }

    private static Parsed parse(String url) {
        Matcher m = UPLOAD_URL.matcher(url);
        if (!m.matches() || m.group(2).contains("..")) return null;
        return new Parsed(m.group(1), m.group(2), m.group(3), m.group(4));
    }

    private record Parsed(String prefix, String rel, String expires, String signature) {
    }
}

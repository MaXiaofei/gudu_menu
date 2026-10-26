package com.gudu.xsd.common;

/**
 * 业务异常。默认 code = 1；鉴权/限流类用 {@link #forbidden}/{@link #tooManyRequests} 携带对应业务码。
 */
public class BizException extends RuntimeException {

    /** 已登录但无权限。 */
    public static final int FORBIDDEN = 403;
    /** 调用频率/额度超限。 */
    public static final int TOO_MANY_REQUESTS = 429;
    /** 资源不存在或不属于当前家庭（跨家庭访问统一 404，不泄露存在性）。 */
    public static final int NOT_FOUND = 404;
    /** 状态冲突（如账号合并 ACCOUNT_WECHAT_CONFLICT）。 */
    public static final int CONFLICT = 409;
    /** 保存敏感个人信息前缺少单独同意（B.2，见 {@link ConsentRequiredException}）。 */
    public static final int CONSENT_REQUIRED = 428;
    /** 内容安全：内容未通过检测（客户端按场景给友好提示）。 */
    public static final int CONTENT_BLOCKED = 451;
    /** 内容安全：检测服务暂不可用且该场景失败即拒绝（昵称/头像），请稍后再试。 */
    public static final int CONTENT_CHECK_UNAVAILABLE = 452;
    /** 账号已被平台封禁：登录与写接口统一返回此码（客户端提示原因并回到登录页）。 */
    public static final int ACCOUNT_BANNED = 423;

    private final int code;

    public BizException(String msg) {
        this(1, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static BizException forbidden(String msg) {
        return new BizException(FORBIDDEN, msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(NOT_FOUND, msg);
    }

    public static BizException tooManyRequests(String msg) {
        return new BizException(TOO_MANY_REQUESTS, msg);
    }

    public static BizException contentBlocked(String msg) {
        return new BizException(CONTENT_BLOCKED, msg);
    }

    public static BizException contentCheckUnavailable(String msg) {
        return new BizException(CONTENT_CHECK_UNAVAILABLE, msg);
    }

    public static BizException accountBanned(String reason) {
        return new BizException(ACCOUNT_BANNED, "账号已被封禁" + (reason == null || reason.isBlank() ? "" : "：" + reason));
    }

    public int getCode() {
        return code;
    }
}

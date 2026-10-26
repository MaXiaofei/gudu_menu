package com.gudu.xsd.common;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>未知异常默认只回通用提示 + traceId，根因只进日志（防 SQL/表名外泄）；
 * {@code gudu.expose-error-detail=true}（仅本地/测试环境）时附带异常类型与根因首行便于排查。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String GENERIC_MSG = "服务器开小差了，请稍后再试";

    @Value("${gudu.expose-error-detail:false}")
    private boolean exposeDetail;

    @ExceptionHandler(BizException.class)
    public R<?> biz(BizException e) {
        return new R<>(e.getCode(), e.getMessage(), null);
    }

    /** 缺少敏感信息单独同意（B.2）：code=428，data = {reason: CONSENT_REQUIRED, docTypes: [...]}，前端据此弹同意窗后重试。 */
    @ExceptionHandler(ConsentRequiredException.class)
    public R<?> consentRequired(ConsentRequiredException e) {
        return new R<>(e.getCode(), e.getMessage(),
                java.util.Map.of("reason", ConsentRequiredException.REASON, "docTypes", e.docTypes()));
    }

    /** 家庭访问被拒（multi-tenant-family）：code=403，data.reason=FAMILY_ACCESS_DENIED，前端据此回到家庭选择。 */
    @ExceptionHandler(com.gudu.xsd.common.tenant.TenantAccessDeniedException.class)
    public R<?> familyDenied(com.gudu.xsd.common.tenant.TenantAccessDeniedException e) {
        return new R<>(e.getCode(), e.getMessage(),
                java.util.Map.of("reason", com.gudu.xsd.common.tenant.TenantAccessDeniedException.REASON));
    }

    /** 领域不变量校验失败（四层新代码抛 DomainException）：业务失败 code=1，msg 可直接展示。 */
    @ExceptionHandler(com.gudu.xsd.common.domain.DomainException.class)
    public R<?> domain(com.gudu.xsd.common.domain.DomainException e) {
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<?> notLogin(NotLoginException e) {
        return new R<>(401, "未登录", null);
    }

    /** @Valid 校验失败（JSON body 与表单绑定）：回第一条字段提示。 */
    @ExceptionHandler(BindException.class)
    public R<?> invalid(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        return R.fail(fe != null && fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "参数不合法");
    }

    /** 路径不存在（含生产关闭的接口文档 /v3/api-docs、/swagger-ui）：HTTP 404，不当作服务端异常记 ERROR。 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<?> notFound(NoResourceFoundException e) {
        return new R<>(404, "接口不存在", null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public R<?> badRequest(Exception e) {
        log.info("请求参数错误：{}", e.getMessage());
        // 请求体里的上传地址签名缺失/失效（A.3，由 UploadUrlJacksonModule 反序列化时抛出、Jackson 包了一层）
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof com.gudu.xsd.common.upload.UploadUrlRejectedException) return R.fail(t.getMessage());
        }
        return R.fail("参数不合法");
    }

    @ExceptionHandler(Exception.class)
    public R<?> all(Exception e) {
        String traceId = MDC.get("traceId");
        log.error("全局异常兜底 traceId={}", traceId, e);
        StringBuilder msg = new StringBuilder(GENERIC_MSG);
        if (traceId != null) msg.append("（").append(traceId).append("）");
        if (exposeDetail) {
            msg.append(" [").append(e.getClass().getSimpleName()).append(": ")
                    .append(abbreviate(rootMessage(e))).append(']');
        }
        return R.fail(msg.toString());
    }

    private static String rootMessage(Throwable e) {
        String root = e.getMessage() == null ? "" : e.getMessage();
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
            if (cur.getMessage() != null) root = cur.getMessage();
        }
        return root;
    }

    private static String abbreviate(String s) {
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}

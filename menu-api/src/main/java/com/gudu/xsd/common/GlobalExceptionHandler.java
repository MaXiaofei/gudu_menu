package com.gudu.xsd.common;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public R<?> badRequest(Exception e) {
        log.info("请求参数错误：{}", e.getMessage());
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

package com.gudu.xsd.common;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<?> biz(BizException e) {
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<?> notLogin(NotLoginException e) {
        return new R<>(401, "未登录", null);
    }

    @ExceptionHandler(Exception.class)
    public R<?> all(Exception e) {
        e.printStackTrace();
        // 诊断辅助（staging）：透出异常类型+首行消息，前端/接口可见，便于定位（如清库后表缺失）
        String root = e.getMessage() == null ? "" : e.getMessage();
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
            if (cur.getMessage() != null) root = cur.getMessage();
        }
        return R.fail("服务器异常[" + e.getClass().getSimpleName() + ": "
                + (root.length() > 120 ? root.substring(0, 120) : root) + "]");
    }
}

package com.yanhuo.xsd.common;

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
        return R.fail("服务器异常");
    }
}

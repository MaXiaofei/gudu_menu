package com.gudu.xsd.modules.ai.gateway.application;

import com.gudu.xsd.common.BizException;

/** 路由上的真实模型全部失败、且调用方没有提供规则兜底：调用方应自行降级。 */
public class ModelUnavailableException extends BizException {

    public static final int CODE = 503;

    public ModelUnavailableException(String message) {
        super(CODE, message);
    }
}

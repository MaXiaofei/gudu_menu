package com.gudu.xsd.common.domain;

/**
 * 领域不变量被破坏（非法档位、超长备注、非法状态流转…）。
 * 由 GlobalExceptionHandler 转为 R{code=1, msg}，msg 可直接展示给用户。
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}

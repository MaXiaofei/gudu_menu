package com.yanhuo.xsd.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结构。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class R<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok(T d) {
        return new R<>(0, "ok", d);
    }

    public static <T> R<T> fail(String m) {
        return new R<>(1, m, null);
    }
}

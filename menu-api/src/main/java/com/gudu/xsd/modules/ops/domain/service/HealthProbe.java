package com.gudu.xsd.modules.ops.domain.service;

/**
 * 健康探测端口：一个实现探一项依赖。check() 正常返回说明文字（如版本、启动时长），失败抛异常。
 * 超时由应用层统一控制，实现里无需自己处理。
 */
public interface HealthProbe {

    String key();

    String label();

    String check() throws Exception;
}

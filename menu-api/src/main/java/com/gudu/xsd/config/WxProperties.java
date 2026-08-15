package com.gudu.xsd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置（appid/secret 走环境变量 WX_APPID / WX_APPSECRET，不进代码库）。
 * 预发/生产各自注入自己的小程序主体。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx")
public class WxProperties {

    /** 小程序 AppID。 */
    private String appid = "";

    /** 小程序 AppSecret。 */
    private String secret = "";
}

package com.gudu.xsd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 系统时钟（四层应用服务注入 Clock 取「今天」，单测可替换为固定时钟）。 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

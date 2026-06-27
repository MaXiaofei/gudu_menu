package com.gudu.xsd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.gudu.xsd.modules.**.mapper")
@EnableScheduling
public class GuduApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuduApplication.class, args);
    }
}

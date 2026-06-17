package com.yanhuo.xsd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yanhuo.xsd.modules.**.mapper")
public class YanhuoApplication {

    public static void main(String[] args) {
        SpringApplication.run(YanhuoApplication.class, args);
    }
}

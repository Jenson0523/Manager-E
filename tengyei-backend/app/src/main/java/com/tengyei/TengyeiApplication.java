package com.tengyei;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.tengyei")
@MapperScan("com.tengyei.**.mapper")
@EnableScheduling
public class TengyeiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TengyeiApplication.class, args);
    }
}

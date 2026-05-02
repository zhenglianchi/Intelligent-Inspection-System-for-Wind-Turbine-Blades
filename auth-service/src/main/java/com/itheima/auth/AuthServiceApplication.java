package com.itheima.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 认证服务启动类
 *
 * @Author AAA
 */
@SpringBootApplication(scanBasePackages = "com.itheima")
@MapperScan("com.itheima.consultant.mapper")
@EnableAsync
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

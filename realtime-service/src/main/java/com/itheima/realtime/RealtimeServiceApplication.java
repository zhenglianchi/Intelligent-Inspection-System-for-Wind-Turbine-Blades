package com.itheima.realtime;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 实时数据服务 - 启动类
 * 提供风机实时监测数据接收、存储、分析和缓存服务
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@SpringBootApplication(scanBasePackages = "com.itheima")
@MapperScan("com.itheima.realtime.mapper")
@EnableAsync
public class RealtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}

package com.itheima.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway 启动类
 * 独立部署的网关服务，负责：
 * 1. 统一入口接收所有外部请求
 * 2. 基于 Nacos 服务发现动态路由
 * 3. 负载均衡到后端 wind-power-agent 实例
 * 4. 统一跨域处理
 * 5. 可以添加限流、认证、日志等横切关注点
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

package com.itheima.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator windPowerRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("agent-service-api", r -> r.path("/api/**").uri("lb://agent-service"))
                .route("realtime-service-realtime", r -> r.path("/realtime/**").uri("lb://realtime-service"))
                .route("realtime-service-windturbine", r -> r.path("/windturbine/**").uri("lb://realtime-service"))
                .route("realtime-service-search", r -> r.path("/searchMaxWindturbineId/**").uri("lb://realtime-service"))
                .route("realtime-service-windfarms", r -> r.path("/windfarms/**").uri("lb://realtime-service"))
                .route("auth-service-user", r -> r.path("/user/**").uri("lb://auth-service"))
                .build();
    }
}

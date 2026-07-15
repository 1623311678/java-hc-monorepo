package com.hc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关 — 所有请求的统一入口
 *
 * 职责:
 * 1. 路由转发 — 根据路径分发到各微服务
 * 2. JWT 鉴权 — 校验 token，解析 userId 放入 header
 * 3. Sentinel 限流 — 秒杀场景热点参数限流
 * 4. 负载均衡 — 多实例轮询/权重
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

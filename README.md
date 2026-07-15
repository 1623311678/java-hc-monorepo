# HC E-Commerce — 电商高并发微服务架构

> Spring Boot 3 + Spring Cloud Alibaba + Next.js + Docker 全栈项目

## 架构图

```
                          ┌─────────────┐
                          │   用户/APP   │
                          └──────┬──────┘
                                 │
                          ┌──────▼──────┐
                          │    Nginx    │  反向代理 + 限流 + 静态资源
                          │   :80/:443  │
                          └──────┬──────┘
                    ┌────────────┴────────────┐
                    │                         │
             ┌──────▼──────┐          ┌───────▼──────┐
             │  Next.js    │          │ Spring Cloud │
             │  前端 :3000  │          │   Gateway    │
             │  (SSR/CSR)  │          │   :8080      │
             └─────────────┘          └───────┬──────┘
                                               │
              ┌─────────────────────────────────┼──────────────────────────┐
              │           Spring Cloud Alibaba 微服务集群                    │
              │                                                              │
       ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
       │   user      │  │  product    │  │   cart      │  │   order     │  │  payment    │  │   search    │
       │   :8081     │  │   :8082     │  │   :8083     │  │   :8084     │  │   :8085     │  │   :8086     │
       │  注册/登录   │  │  商品/SKU   │  │  购物车     │  │  下单/秒杀   │  │  支付/退款   │  │  ES 搜索    │
       └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
              │                │                │                │                │                │
       ┌──────┴────────────────┴────────────────┴────────────────┴────────────────┴────────────────┴──────┐
       │                                     基础设施层                                                   │
       │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
       │  │  MySQL   │  │  Redis   │  │ RabbitMQ │  │  Nacos   │  │ Sentinel │  │Seata分布式│            │
       │  │ 每库独立  │  │ 缓存/会话│  │ 异步消息  │  │注册/配置 │  │ 限流熔断 │  │  事务     │            │
       │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
       └────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层 | 技术 | 核心能力 |
|---|------|---------|
| **网关** | Spring Cloud Gateway | 路由、限流、鉴权、灰度 |
| **注册中心** | Nacos | 服务发现 + 动态配置 |
| **限流熔断** | Sentinel | 流控、熔断降级、热点参数限流 |
| **分布式事务** | Seata | 订单→库存→支付 一致性 |
| **缓存** | Redis + Redisson | 热数据缓存、分布式锁(秒杀)、会话 |
| **消息队列** | RabbitMQ | 异步下单、库存扣减、支付回调 |
| **数据库** | MySQL 8 (每服务独立库) | 读写分离、分库分表预留 |
| **搜索** | Elasticsearch | 商品全文搜索 |
| **ORM** | MyBatis Plus | 单表 CRUD + 复杂 SQL |
| **API 文档** | Knife4j (Swagger 3) | 在线调试 API |
| **前端** | Next.js 14 + Tailwind | SSR 电商页面 |
| **部署** | Docker Compose | 一键启动全栈 |
| **监控** | Spring Boot Actuator + Prometheus | 健康检查 + 指标采集 |

## 快速开始

```bash
# 1. 启动基础设施 (MySQL/Redis/Nacos/RabbitMQ)
make infra

# 2. 启动所有微服务
make services

# 3. 启动前端
make frontend

# 4. 一键全启动
make dev

# 5. 访问
# 前端:      http://localhost
# API网关:    http://localhost/api
# Nacos控制台: http://localhost:8848/nacos (nacos/nacos)
# Sentinel:   http://localhost:8080 (sentinel/sentinel)
# RabbitMQ:   http://localhost:15672 (admin/admin123)
```

## 项目结构

```
hc-ecommerce/
├── pom.xml                          # 父 POM (统一版本管理)
├── docker-compose.yml               # 基础设施编排
├── docker-compose.services.yml      # 微服务编排
├── Makefile                         # 快捷命令
├── .env.example
│
├── services/                        # 6 个微服务
│   ├── gateway/                     # Spring Cloud Gateway
│   ├── user/                        # 用户服务 (注册/登录/OAuth)
│   ├── product/                     # 商品服务 (SPU/SKU/库存)
│   ├── cart/                        # 购物车服务 (Redis 缓存)
│   ├── order/                       # 订单服务 (下单/秒杀/Seata)
│   └── payment/                     # 支付服务 (支付宝/微信)
│
├── frontend/                        # Next.js 14 电商
│   └── src/
│       ├── app/pages/               # 首页/商品列表/详情/购物车/订单
│       ├── components/              # 商品卡片/购物车栏/订单状态
│       ├── store/                   # Zustand 全局状态
│       └── lib/                     # API 客户端/工具函数
│
├── deploy/
│   ├── docker/                      # 各服务 Dockerfile
│   ├── nginx/                       # Nginx 配置
│   ├── scripts/                     # 部署/备份脚本
│   └── monitor/                     # Prometheus + Grafana
│
└── docs/
    ├── architecture.md              # 架构设计文档
    ├── high-concurrency.md          # 高并发方案 (秒杀/限流/缓存)
    └── distributed-transaction.md   # 分布式事务方案
```

## 学习路线

1. **Docker Compose** → 理解基础设施编排
2. **Nacos** → 服务注册发现 + 动态配置
3. **Spring Cloud Gateway** → 统一入口、路由、鉴权
4. **MyBatis Plus + MySQL** → 数据访问
5. **Redis + Redisson** → 缓存 + 分布式锁 (秒杀核心)
6. **RabbitMQ** → 异步消息 (下单→库存→支付)
7. **Sentinel** → 限流熔断 (秒杀防刷)
8. **Seata** → 分布式事务 (订单一致性)
9. **Elasticsearch** → 商品搜索
10. **Next.js** → 电商前端
11. **Docker 部署** → 一键上线

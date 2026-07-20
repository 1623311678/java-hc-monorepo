# 从本地开发到生产部署 — 完整指南

> 面向：前端转全栈开发者  
> 目标：理解本地开发、Docker Compose 部署、生产上线的全链路区别与演进

---

## 目录

1. [三种环境对比](#1-三种环境对比)
2. [本地开发模式](#2-本地开发模式)
3. [Docker Compose 单机部署](#3-docker-compose-单机部署)
4. [生产环境部署](#4-生产环境部署)
5. [CI/CD 自动化部署](#5-cicd-自动化部署)
6. [配置分离策略](#6-配置分离策略)
7. [从本地到生产的升级路线](#7-从本地到生产的升级路线)
8. [前端类比对照表](#8-前端类比对照表)

---

## 1. 三种环境对比

### 总览

```
本地开发                        Docker Compose 部署           生产环境
─────────                      ──────────────────            ──────────
你的 Mac                        一台云服务器                   多台服务器 / K8s
改代码秒生效                     docker compose up -d          CI/CD 自动部署
密码 root/admin123              强密码                         密钥管理服务
MySQL 在容器里                   MySQL 在容器里                 阿里云 RDS
Redis 在容器里                  Redis 在容器里                 阿里云 Redis
HTTP (无 HTTPS)                可能加 HTTPS                   必须 HTTPS
1 个实例/服务                    1 个实例/服务                  N 个实例/服务
```

### 详细对比表

| 维度 | 本地开发 | Docker Compose 部署 | 生产环境 |
|------|---------|-------------------|---------|
| **跑在哪** | 你的 Mac | 一台云服务器 | 多台服务器 / K8s 集群 |
| **基础设施** | Docker 容器 | Docker 容器 | 云托管服务（RDS/ElastiCache） |
| **微服务数量** | 每种 1 个 | 每种 1 个 | 每种 N 个（按需扩容） |
| **MySQL** | 容器内 MySQL | 容器内 MySQL | 云数据库（阿里云 RDS / AWS RDS） |
| **Redis** | 容器内 Redis | 容器内 Redis | 云 Redis（阿里云 Redis / ElastiCache） |
| **密码管理** | .env 明文 | .env 明文（强密码） | 密钥管理服务（KMS）/ Docker Secrets |
| **网络** | hc-net 桥接 | hc-net 桥接 | VPC 隔离 + 安全组 |
| **存储** | Docker 卷 | Docker 卷 + 云盘 | 云盘 / EBS / OSS |
| **HTTPS** | 没有 | 可能加 | 必须（证书 + WAF） |
| **监控** | 看日志 | Prometheus + Grafana | 全链路监控 + 告警 + 日志服务 |
| **高可用** | 挂了就挂了 | restart: always | 多副本 + 自动故障转移 |
| **备份** | 没有 | 手动 mysqldump | 自动每日备份 + 时间点恢复 |
| **日志** | 终端看 | docker compose logs | ELK / 云日志服务 |
| **目的** | 快速开发调试 | 低成本部署验证 | 稳定安全高性能 |

---

## 2. 本地开发模式

### 架构图

```
┌────────────────── 你的 Mac ──────────────────┐
│                                              │
│  Docker 容器（基础设施）                       │
│  ├── MySQL :3306                             │
│  ├── Redis :6379                             │
│  ├── Nacos :8848                             │
│  ├── RabbitMQ :5672                          │
│  └── Elasticsearch :9200                     │
│                                              │
│  IDE / 终端（微服务本地跑）                     │
│  ├── UserApplication :7101   ← 改代码秒生效   │
│  ├── ProductApplication :7102               │
│  └── OrderApplication :7104                  │
│                                              │
│  npm run dev（前端本地跑）                     │
│  └── Next.js :3000                           │
└──────────────────────────────────────────────┘
```

### 启动命令

```bash
# 1. 只启动基础设施（Docker）
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel

# 2. 微服务在 IDE 里跑（不用 Docker）
# IntelliJ IDEA: 点 Run 按钮
# 或终端:
cd services/user && mvn spring-boot:run
cd services/product && mvn spring-boot:run
cd services/order && mvn spring-boot:run

# 3. 前端本地跑
cd frontend && npm run dev
```

### 特点

| 优点 | 缺点 |
|------|------|
| ⚡ 快（改代码秒生效） | 不是完整链路（没走 Nginx/Gateway） |
| 🐛 好调试（断点、变量查看） | 和生产环境有差异 |
| 📝 日志清晰 | 端口直接暴露，没有限流/鉴权 |

### 适用场景

- 日常写代码、改 Bug、开发新功能
- 占你 **80%** 的工作时间

---

## 3. Docker Compose 单机部署

### 架构图

```
┌────────────────── 一台云服务器 ──────────────┐
│                                             │
│  Docker Compose 管理全部 14 个容器             │
│                                             │
│  基础设施层:                                  │
│  ├── MySQL :3306                            │
│  ├── Redis :6379                            │
│  ├── Nacos :8848                            │
│  ├── RabbitMQ :5672/:15672                   │
│  ├── Elasticsearch :9200                    │
│  ├── Seata :8091                            │
│  └── Sentinel :8858(→localhost:8070)                         │
│                                             │
│  微服务层:                                    │
│  ├── Nginx :80/:443                         │
│  ├── Gateway :7100                          │
│  ├── User :7101                             │
│  ├── Product :7102                          │
│  ├── Cart :7103                             │
│  ├── Order :7104                            │
│  └── Payment :7105                          │
│                                             │
│  前端层:                                     │
│  └── Next.js :3000                          │
│                                             │
│  ⚠️ 一台机器跑 14 个容器，资源紧张但能用       │
│  建议: 4核 16G 内存起步                       │
└─────────────────────────────────────────────┘
```

### 部署步骤

#### 第一步：买服务器

| 配置 | 适合 | 参考价格 |
|------|------|---------|
| 2核 8G | Demo / 测试 | ~100 元/月 |
| 4核 16G | 小规模上线 | ~300 元/月 |
| 8核 32G | 正式运营 | ~600 元/月 |

#### 第二步：服务器安装 Docker

```bash
# CentOS / Alibaba Cloud Linux
curl -fsSL https://get.docker.com | sh
systemctl enable docker && systemctl start docker

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

#### 第三步：上传代码

```bash
# 方式 1: Git 克隆
git clone https://github.com/xxx/java-hc-monorepo.git
cd java-hc-monorepo

# 方式 2: SCP 上传
scp -r ./java-hc-monorepo root@your-server:/opt/
```

#### 第四步：配置环境变量

```bash
cp .env.example .env

# 编辑 .env，改成强密码！
vi .env
```

```bash
# .env — 生产环境绝不能用默认密码！
MYSQL_PASSWORD=Str0ng!P@ssw0rd#2024
REDIS_PASSWORD=R3d1s!S3cur3#2024
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=RbtMq!Pr0d#2024
JWT_SECRET=Th1sIsAV3ryL0ngAndS3cur3JWTSecretKeyF0rPr0duct10n2024!@#
NACOS_NAMESPACE=prod
```

#### 第五步：启动

```bash
# 使用生产配置启动
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

#### 第六步：配置 HTTPS（必须）

```bash
# 用 Let's Encrypt 免费证书
# 安装 certbot
apt install certbot

# 申请证书
certbot certonly --standalone -d your-domain.com

# 证书路径
# /etc/letsencrypt/live/your-domain.com/fullchain.pem
# /etc/letsencrypt/live/your-domain.com/privkey.pem
```

### 优缺点

| 优点 | 缺点 |
|------|------|
| 部署简单，一条命令 | 单机瓶颈，无法横向扩展 |
| 跟本地开发一致 | 数据库在容器里，没有自动备份 |
| 成本低（一台服务器） | 挂了就全挂了，没有高可用 |
| 适合 MVP / 小规模 | 14 个容器抢资源，性能有限 |

### 适用场景

- 个人项目 / Demo 演示
- 小团队 MVP 快速验证
- 占你 **5%** 的工作时间（偶尔部署）

---

## 4. 生产环境部署

### 架构图

```
┌──── CDN ────┐
│  静态资源     │
│  图片/JS/CSS │
└──────┬───────┘
       │
┌──────▼───────┐        ┌──── 云托管数据库 ────┐
│   WAF 防火墙  │        │                    │
│   SSL 终止    │        │  阿里云 RDS         │
└──────┬───────┘        │  (MySQL 主从)       │
       │                │                    │
┌──────▼───────┐        │  阿里云 Redis       │
│   SLB 负载均衡│        │  (集群版)           │
└──────┬───────┘        │                    │
       │                │  阿里云 ES          │
┌──────▼────────────────┐│  (商品搜索)         │
│   K8s 集群 / 多台服务器 ││                    │
│                       ││  阿里云 MQ          │
│  gateway × 3          ││  (消息队列)         │
│  product × 2          ├►│                    │
│  order × 3            ││  Nacos             │
│  user × 2             ││  (独立部署)          │
│  cart × 2             ││                    │
│  payment × 2          │└────────────────────┘
│                       │
│  秒杀时:               │  ┌──── 监控 ────────┐
│  order 自动扩到 × 10  │  │ Prometheus       │
│                       │  │ Grafana          │
└───────────────────────┘  │ 日志服务(SLS)     │
                          │ 告警通知          │
                          └──────────────────┘
```

### 关键差异：生产不用容器跑数据库！

| 组件 | 本地/Docker Compose | 生产环境 | 原因 |
|------|-------------------|---------|------|
| **MySQL** | Docker 容器 | **云数据库 RDS** | 自动备份、主从同步、故障恢复 |
| **Redis** | Docker 容器 | **云 Redis** | 集群版、自动扩容、持久化 |
| **RabbitMQ** | Docker 容器 | **云消息队列** | 高可用、消息不丢 |
| **Elasticsearch** | Docker 容器 | **云 ES** | 索引自动管理、扩容 |
| **Nacos** | Docker 容器 | **独立部署 / Nacos 集群** | 注册中心不能挂 |
| **微服务** | Docker 容器 | **Docker 容器 / K8s Pod** | 只有无状态服务适合容器化 |

> 💡 **原则：有状态的服务（数据库）用云托管，无状态的服务（微服务）用容器。**

### 生产配置示例

```yaml
# docker-compose.prod.yml — 生产环境覆盖配置

services:
  # ===== 基础设施：生产不用容器跑，全部删掉 =====
  # mysql:     删掉 → 用阿里云 RDS
  # redis:     删掉 → 用阿里云 Redis
  # rabbitmq:  删掉 → 用阿里云 MQ
  # elasticsearch: 删掉 → 用阿里云 ES

  # ===== 微服务：改用云数据库地址 =====
  hc-product:
    environment:
      MYSQL_HOST: rm-xxxxx.mysql.rds.aliyuncs.com
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}     # 从安全的地方注入
      REDIS_HOST: r-xxxxx.redis.rds.aliyuncs.com
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      RABBITMQ_HOST: amqp-xxxxx.mq.aliyuncs.com
      ES_HOST: es-xxxxx.elasticsearch.aliyuncs.com
    deploy:
      replicas: 2    # ← 跑 2 个实例
      resources:
        limits:
          memory: 512M
          cpus: '0.5'
    restart: always

  hc-order:
    environment:
      MYSQL_HOST: rm-xxxxx.mysql.rds.aliyuncs.com
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: r-xxxxx.redis.rds.aliyuncs.com
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 512M
    restart: always

  # ===== Nginx: 加 HTTPS =====
  nginx:
    ports:
      - "443:443"
      - "80:80"
    volumes:
      - ./deploy/nginx/nginx.prod.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt/live/your-domain.com/fullchain.pem:/etc/nginx/ssl/cert.pem:ro
      - /etc/letsencrypt/live/your-domain.com/privkey.pem:/etc/nginx/ssl/key.pem:ro
```

---

## 5. CI/CD 自动化部署

### 流程图

```
开发者 → git push → GitHub → GitHub Actions 自动执行
                                  │
                                  ├── 1. mvn package (编译)
                                  ├── 2. docker build (构建镜像)
                                  ├── 3. docker push (推到镜像仓库)
                                  └── 4. SSH 到服务器 → docker compose pull && up -d
```

### GitHub Actions 配置示例

```yaml
# .github/workflows/deploy.yml
name: Deploy to Production

on:
  push:
    branches: [main]   # main 分支推送时自动部署

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      # 1. 拉取代码
      - uses: actions/checkout@v4

      # 2. 登录镜像仓库（阿里云 ACR）
      - name: Login to Registry
        run: |
          docker login registry.cn-hangzhou.aliyuncs.com \
            -u ${{ secrets.REGISTRY_USER }} \
            -p ${{ secrets.REGISTRY_PASSWORD }}

      # 3. 构建并推送镜像
      - name: Build & Push
        run: |
          docker compose build
          docker tag java-hc-monorepo-gateway registry.cn-hangzhou.aliyuncs.com/hc/gateway:latest
          docker push registry.cn-hangzhou.aliyuncs.com/hc/gateway:latest
          # ... 其他服务类似

      # 4. 部署到服务器
      - name: Deploy
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: root
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/java-hc-monorepo
            docker compose pull
            docker compose up -d
```

### 需要配置的 GitHub Secrets

| Secret 名 | 值 | 说明 |
|-----------|---|------|
| `REGISTRY_USER` | 阿里云 ACR 用户名 | 镜像仓库账号 |
| `REGISTRY_PASSWORD` | 阿里云 ACR 密码 | 镜像仓库密码 |
| `SERVER_HOST` | 你的服务器 IP | 部署目标 |
| `SERVER_SSH_KEY` | SSH 私钥 | 连接服务器用 |

---

## 6. 配置分离策略

### 核心原则

```
┌─────────────────────────────────────────────────┐
│  同一套代码，同一套镜像                              │
│  只有配置不同！                                    │
│                                                  │
│  代码:        services/order/OrderService.java    │
│  镜像:        hc-order:latest                     │
│                                                  │
│  本地配置:    MYSQL_HOST=mysql (容器名)             │
│  生产配置:    MYSQL_HOST=rm-xxx.rds.aliyuncs.com  │
└─────────────────────────────────────────────────┘
```

### 配置文件结构

```
项目根目录/
├── docker-compose.yml           # 基础定义（所有环境共用）
├── docker-compose.prod.yml      # 生产覆盖（密码、云地址、副本数）
├── .env                         # 本地开发配置
├── .env.prod                    # 生产环境配置（不入 Git）
│
├── services/order/
│   └── src/main/resources/
│       ├── application.yml      # 公共配置
│       ├── application-dev.yml  # 本地开发（localhost）
│       └── application-prod.yml # 生产环境（云地址）
```

### application.yml 配置切换

```yaml
# services/order/src/main/resources/application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES:dev}  # 默认 dev，生产传 prod

---
# application-dev.yml（本地开发）
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/hc_order
  data:
    redis:
      host: ${REDIS_HOST:localhost}

---
# application-prod.yml（生产环境）
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:3306/hc_order?useSSL=true
  data:
    redis:
      host: ${REDIS_HOST}
      password: ${REDIS_PASSWORD}
```

### 启动命令对比

```bash
# 本地开发
docker compose up -d
# → 读 docker-compose.yml + .env
# → 微服务 active profile = dev

# 生产部署
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
# → 读 docker-compose.yml + docker-compose.prod.yml + .env.prod
# → 微服务 active profile = prod
```

---

## 7. 从本地到生产的升级路线

### Level 1: 本地开发（你现在在这）

```
你的 Mac + Docker Compose
├── 基础设施: Docker 容器
├── 微服务: IDE 里跑
├── 前端: npm run dev
└── 密码: root/admin123
```

**学习重点**：理解微服务架构，写业务代码

### Level 2: Docker Compose 单机部署

```
一台云服务器 (4核16G)
├── 全部 14 个容器
├── docker-compose.prod.yml (强密码)
├── HTTPS (Let's Encrypt)
└── 域名绑定
```

**学习重点**：Linux 服务器操作、HTTPS 配置、安全意识

**部署清单**：

- [ ] 买云服务器
- [ ] 装 Docker + Docker Compose
- [ ] 克隆代码
- [ ] 创建 .env（强密码）
- [ ] 创建 docker-compose.prod.yml
- [ ] 申请域名 + 备案
- [ ] 配置 HTTPS (Let's Encrypt)
- [ ] `docker compose up -d`
- [ ] 配置防火墙（只开 80/443）

### Level 3: 数据库上云

```
应用服务器 (Docker Compose)         云托管服务
├── gateway × 1                   ├── 阿里云 RDS (MySQL)
├── hc-product × 2                ├── 阿里云 Redis
├── hc-order × 2                  ├── 阿里云 MQ (RabbitMQ)
├── hc-user × 1                   └── 阿里云 ES
├── hc-cart × 1
├── hc-payment × 1
├── nginx
└── frontend
```

**学习重点**：云服务选型、VPC 网络配置、数据库迁移

**升级清单**：

- [ ] 创建阿里云 RDS 实例
- [ ] 创建阿里云 Redis 实例
- [ ] 导入本地数据到 RDS（mysqldump）
- [ ] 修改 docker-compose.prod.yml 指向云数据库
- [ ] 配置 VPC 让应用服务器能访问 RDS
- [ ] 微服务扩到 2 副本
- [ ] 配置自动备份

### Level 4: Kubernetes 集群

```
K8s 集群
├── Ingress Controller (替代 Nginx)
├── gateway Deployment × 3
├── product Deployment × 2
├── order Deployment × 3~10 (自动扩缩)
├── user Deployment × 2
├── cart Deployment × 2
├── payment Deployment × 2
├── HPA (水平自动扩缩容)
├── Service (内部负载均衡)
└── ConfigMap / Secret (配置管理)
```

**学习重点**：K8s 概念（Pod/Service/Deployment/HPA）、Helm Chart

**升级清单**：

- [ ] 创建 K8s 集群（阿里云 ACK / AWS EKS）
- [ ] 编写 K8s 部署清单（Deployment + Service + Ingress）
- [ ] 配置 HPA（秒杀时 Order Pod 自动扩容）
- [ ] 配置 ConfigMap 和 Secret
- [ ] 部署 Prometheus + Grafana 监控
- [ ] 配置告警规则
- [ ] CI/CD 改为部署到 K8s

---

## 8. 前端类比对照表

| 后端概念 | 前端等价 | 说明 |
|---------|---------|------|
| `docker compose up -d` | `npm run dev` | 本地开发启动 |
| `docker compose -f prod.yml up -d` | `npm run build && npm start` | 生产构建启动 |
| `mvn package` | `npm run build` | 编译打包 |
| `Docker 镜像` | `Docker 镜像` | 前后端都一样 |
| 阿里云 RDS | Supabase / PlanetScale | 托管数据库 |
| 阿里云 Redis | Upstash Redis | 托管 Redis |
| K8s HPA | Vercel Auto-scaling | 自动扩缩容 |
| Nginx 反向代理 | Vercel Edge Network | 流量入口 |
| CI/CD (GitHub Actions) | CI/CD (Vercel Deploy) | 自动部署 |
| `.env` vs `.env.prod` | `.env.local` vs `.env.production` | 配置分离 |
| `application-dev.yml` | `next.config.dev.js` | 开发配置 |
| `application-prod.yml` | `next.config.prod.js` | 生产配置 |
| SSL 证书 (Let's Encrypt) | Vercel 自动 HTTPS | 加密传输 |
| WAF 防火墙 | Cloudflare | 安全防护 |
| SLB 负载均衡 | Cloudflare Load Balancing | 流量分发 |
| Prometheus + Grafana | Vercel Analytics | 监控面板 |
| 阿里云日志服务 (SLS) | Sentry | 日志/错误追踪 |

---

## 部署检查清单

### 上线前必须确认

- [ ] 所有密码已改为强密码（不是 root/admin123）
- [ ] HTTPS 已配置（Let's Encrypt 或云证书）
- [ ] 数据库有自动备份策略
- [ ] 防火墙只开放必要端口（80/443）
- [ ] Redis 设置了密码
- [ ] Nacos 开启了认证
- [ ] JVM 内存参数根据服务器配置调整
- [ ] 日志输出到文件/日志服务（不只在终端）
- [ ] 健康检查端点可用（`/actuator/health`）
- [ ] 监控和告警已配置

### 上线后日常运维

- [ ] 定期检查容器状态 `docker compose ps`
- [ ] 查看日志是否有异常 `docker compose logs --tail=100`
- [ ] 监控磁盘/内存/CPU 使用率
- [ ] 定期更新 Docker 镜像版本（安全补丁）
- [ ] 数据库定期备份验证（能恢复才是真备份）

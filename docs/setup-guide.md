# 电商高并发微服务 — 本地环境搭建与启动指南

> 适用系统：macOS (Apple Silicon M1/M2/M3/M4)  
> 目标读者：前端开发者转全栈，从零搭建 Java 微服务开发环境

---

## 目录

1. [架构总览](#1-架构总览)
2. [环境要求检查](#2-环境要求检查)
3. [安装软件](#3-安装软件)
4. [项目配置](#4-项目配置)
5. [启动项目](#5-启动项目)
6. [验证服务](#6-验证服务)
7. [日常操作](#7-日常操作)
8. [常见问题排查](#8-常见问题排查)
9. [关键概念速查（前端视角）](#9-关键概念速查前端视角)

---

## 1. 架构总览

```
用户浏览器
    │
    ▼
┌─────────────┐
│   Nginx     │  :80  反向代理 + 限流 + 静态资源
└──────┬──────┘
       │
   ┌───┴───┐
   ▼       ▼
Next.js   Spring Cloud Gateway  :7100  路由/鉴权/限流
:3000            │
     ┌──────────┼──────────────────┐
     ▼          ▼                  ▼
  用户服务    商品服务    订单服务  ... (6个微服务)
  :7101      :7102      :7104
     │          │                  │
     └──────────┼──────────────────┘
                ▼
         基础设施层
  MySQL  Redis  RabbitMQ  Nacos  Sentinel  Seata  ES
```

**14 个容器**：7 个基础设施 + 6 个微服务 + 1 个前端

---

## 2. 环境要求检查

打开终端，逐个检查：

```bash
# 检查 Homebrew（macOS 包管理器）
brew --version
# 需要: Homebrew 4.x+

# 检查 Node.js（前端开发已有）
node --version
# 需要: v18+  ✅ 你已有 v20

# 检查 npm
npm --version
# ✅ 你已有

# 检查 make
make --version
# ✅ macOS 自带
```

**缺失的软件**（下一节安装）：

| 软件 | 用途 | 为什么需要 |
|------|------|-----------|
| OrbStack | Docker 运行时 | 所有基础设施 + 微服务都跑在 Docker 容器里 |
| JDK 21 | Java 运行环境 | 微服务是 Spring Boot 3 + Java 21 |
| Maven | Java 构建工具 | 编译 Java 代码，打包 jar |

---

## 3. 安装软件

### 3.1 安装 OrbStack（Docker 运行时）

OrbStack 是 Docker Desktop 的轻量替代品，内存占用少 50%，启动快 10 倍。

```bash
brew install --cask orbstack
```

安装完成后，**手动启动 OrbStack 应用**（从启动台或 Spotlight 搜索 OrbStack）。

> ⚠️ 必须启动应用后，`docker` 命令才可用。首次启动时 OrbStack 会：
> 1. 创建 Linux 虚拟机
> 2. 在 `/usr/local/bin/` 创建 `docker` 和 `docker compose` 的符号链接

验证：
```bash
docker --version
# 期望: Docker version 2x.x.x

docker compose version
# 期望: Docker Compose version v5.x.x
```

**内存设置**：OrbStack → Settings → Resources，建议分配 **8GB+ 内存**（14 个容器总内存需求较大）。

---

### 3.2 安装 JDK 21

项目 `pom.xml` 指定了 `java.version=21`，必须用 JDK 21。

```bash
brew install openjdk@21
```

配置环境变量：
```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@21"' >> ~/.zshrc
source ~/.zshrc
```

验证：
```bash
java -version
# 期望: openjdk version "21.x.x"

echo $JAVA_HOME
# 期望: /opt/homebrew/opt/openjdk@21
```

---

### 3.3 安装 Maven

```bash
brew install maven
```

验证：
```bash
mvn -version
# 期望: Apache Maven 3.9.x
# 确认 Java version 显示 21.x
```

---

### 3.4 安装验证清单

全部跑完后，执行以下命令确认一切就绪：

```bash
echo "=== 环境检查 ==="
echo "Docker:    $(docker --version 2>&1 | head -1)"
echo "Compose:  $(docker compose version 2>&1 | head -1)"
echo "Java:     $(java -version 2>&1 | head -1)"
echo "Maven:    $(mvn -version 2>&1 | head -1)"
echo "Node:     $(node --version)"
echo "Make:     $(make --version 2>&1 | head -1)"
```

期望输出类似：
```
Docker:    Docker version 29.4.0
Compose:   Docker Compose version v5.1.2
Java:      openjdk version "21.0.11"
Maven:     Apache Maven 3.9.16
Node:      v20.20.2
Make:      GNU Make 3.81
```

---

## 4. 项目配置

### 4.1 克隆项目（如果还没克隆）

```bash
cd ~
git clone https://github.com/1623311678/java-hc-monorepo.git
cd java-hc-monorepo
```

### 4.2 创建环境变量文件

```bash
cp .env.example .env
```

`.env` 内容说明：

```bash
# MySQL root 密码（docker-compose.yml 和微服务都读这个）
MYSQL_PASSWORD=root

# Redis 密码（空 = 无密码，本地开发够用）
REDIS_PASSWORD=

# RabbitMQ 管理员账号
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=admin123

# JWT 签名密钥（生产环境必须换）
JWT_SECRET=mySecretKeyForJwtTokenGenerationMustBe256BitsLong2024!

# Nacos 命名空间
NACOS_NAMESPACE=dev
```

> ⚠️ `.env` 不会被 Git 追踪（已在 `.gitignore` 中），密码修改只影响本地。

---

## 5. 启动项目

### 5.1 第一步：启动基础设施

```bash
cd ~/java-hc-monorepo
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel nginx
```

这会启动 8 个基础设施容器。首次运行需要拉取镜像，大约 **5-10 分钟**。

等待容器就绪（约 15-30 秒）：
```bash
docker compose ps
```

期望看到所有基础设施状态为 `Up`：
```
NAME             STATUS
hc-mysql         Up
hc-redis         Up
hc-nacos         Up
hc-rabbitmq      Up
hc-elasticsearch Up
hc-seata         Up
hc-sentinel      Up
hc-nginx         Up (可能报错，因为 gateway 还没启动，可以忽略)
```

> 💡 Nacos 启动较慢，可能需要多等 10 秒。用 `docker compose logs nacos` 查看是否就绪。

### 5.2 第二步：构建并启动微服务

```bash
docker compose up -d gateway hc-user hc-product hc-cart hc-order hc-payment
```

**首次构建会较慢**（3-5 分钟），因为：
1. Docker 要拉取 `maven:3.9-eclipse-temurin-21` 构建镜像
2. Maven 要下载所有依赖（Spring Boot、MyBatis Plus、Redisson 等）
3. 6 个微服务依次编译打包

第二次起就有 Docker 缓存，只需几十秒。

检查状态：
```bash
docker compose ps
```

### 5.3 第三步：启动前端

```bash
docker compose up -d frontend
```

### 5.4 一键方式（下次用）

以上三步可以用一条命令替代：

```bash
make dev
```

等价于：
1. `make infra` → 启动基础设施
2. 等 15 秒
3. `make services` → 构建并启动微服务
4. `make frontend` → 启动前端

---

## 6. 验证服务

### 6.1 访问地址

| 服务 | URL | 账号/密码 |
|------|-----|----------|
| **电商首页** | http://localhost | — |
| **API 网关** | http://localhost/api | — |
| **Nacos 控制台** | http://localhost:8848/nacos | nacos / nacos |
| **Sentinel 控制台** | http://localhost:8070 | sentinel / sentinel |
| **RabbitMQ 控制台** | http://localhost:15672 | admin / admin123 |
| **Elasticsearch** | http://localhost:9200 | — |
| **Knife4j API 文档** | http://localhost:7102/doc.html (商品服务) | — |

### 6.2 验证基础设施

```bash
# MySQL
docker compose exec mysql mysql -uroot -proot -e "SHOW DATABASES;"
# 期望看到 hc_product, hc_order, hc_user, hc_payment

# Redis
docker compose exec redis redis-cli ping
# 期望: PONG

# Nacos
curl -s http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10
# 期望返回注册的服务列表

# Elasticsearch
curl http://localhost:9200
# 期望返回 ES 版本信息
```

### 6.3 验证微服务

```bash
# 通过 Gateway 访问商品服务
curl http://localhost:7100/product/list
# 或通过 Nginx
curl http://localhost/api/product/list
```

---

## 7. 日常操作

### 启动 / 停止 / 重启

```bash
# 启动全部
docker compose up -d

# 停止全部（数据保留）
docker compose down

# 停止并删除数据卷（⚠️ 数据库、缓存全清空）
docker compose down -v

# 重启某个服务
docker compose restart hc-product

# 查看日志
docker compose logs -f gateway          # 跟踪 gateway 日志
docker compose logs --tail=50 hc-order  # 最近 50 行 order 日志
```

### 进入容器内部

```bash
# 进入 MySQL 命令行
docker compose exec mysql mysql -uroot -proot

# 进入 Redis 命令行
docker compose exec redis redis-cli

# 查看 RabbitMQ 队列
docker compose exec rabbitmq rabbitmqctl list_queues

# 进入某个微服务的 shell
docker compose exec hc-product sh
```

### 数据库操作

```bash
# 重置数据库（⚠️ 清空所有数据，重新执行 init.sql）
make reset-db

# 备份数据库
make backup
```

### 重新构建某个微服务

修改 Java 代码后：
```bash
docker compose build hc-product      # 重新构建商品服务镜像
docker compose up -d hc-product      # 重启商品服务
```

---

## 8. 常见问题排查

### Q1: `no matching manifest for linux/arm64/v8`

**原因**：Apple Silicon Mac 上某些镜像没有 ARM64 版本。

**解决**：在 `docker-compose.yml` 中给该服务加 `platform: linux/amd64`：

```yaml
nacos:
  image: nacos/nacos-server:v2.3.2
  platform: linux/amd64    # ← 加这行，用 x86 模拟运行
```

目前已修复的：Nacos。其他镜像（MySQL、Redis、RabbitMQ、ES、Sentinel、Seata）都有 ARM64 版本。

---

### Q2: `lstat /target: no such file or directory`

**原因**：旧版 Dockerfile.java 要求宿主机先 `mvn package`，但项目还没编译。

**解决**：已修复为多阶段构建，Docker 内部自动完成 Maven 编译。确保 `deploy/docker/Dockerfile.java` 是新版。

---

### Q3: 微服务启动后立刻退出

```bash
# 查看退出日志
docker compose logs hc-product
```

常见原因：
- Nacos 没启动完 → 等久一点再启动微服务
- MySQL 没就绪 → 等 MySQL 日志出现 `ready for connections`
- 端口冲突 → `lsof -i :7102` 检查端口占用

---

### Q4: Nginx 启动失败

**原因**：`docker-compose.yml` 中 Nginx 依赖 `gateway` 和 `frontend`，微服务还没构建时 Nginx 会失败。

**解决**：这是正常的。先启动基础设施，再启动微服务和前端，Nginx 最后会自动就绪。

```bash
# 或者临时先不启动 Nginx，等其他服务都好了再加
docker compose up -d nginx
```

---

### Q5: Maven 构建极慢

首次构建需要下载依赖，约 3-5 分钟。后续有 Docker 层缓存，只需几十秒。

如需加速，可配置国内 Maven 镜像（阿里云）：

在 `~/.m2/settings.xml` 中添加：
```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/central</url>
  </mirror>
</mirrors>
```

---

### Q6: Docker 磁盘空间不足

```bash
# 查看 Docker 磁盘使用
docker system df

# 清理未使用的镜像/容器/缓存
docker system prune -f

# 深度清理（⚠️ 删掉所有未使用的镜像，下次构建需重新拉取）
docker system prune -a
```

---

## 9. 关键概念速查（前端视角）

| Java / Docker 概念 | 前端等价 | 说明 |
|-------------------|---------|------|
| `docker-compose.yml` | `package.json` + `docker-compose.yml` | 定义所有服务的运行方式 |
| `Dockerfile.java` | 前端 `Dockerfile` | 把代码打包成 Docker 镜像 |
| `deploy/mysql/init.sql` | `prisma/seed.ts` | 数据库初始化脚本 |
| `deploy/nginx/nginx.conf` | `vercel.json` 的 rewrites | 反向代理 + 路由 |
| `.env` | `.env.local` | 敏感配置（不提交 Git） |
| `volumes` | `localStorage` | 数据持久化（删容器不丢数据） |
| `networks` | 同一局域网 | 容器间用服务名互访 |
| `depends_on` | `await db.connect()` | 启动顺序依赖 |
| `@RestController` | `app/api/xxx/route.ts` | API 路由处理 |
| `@Service` | `lib/services/xxx.ts` | 业务逻辑层 |
| `@Mapper (Repository)` | `prisma.user.xxx` | 数据库 CRUD |
| `@Data Model` | TypeScript `interface` | 数据结构定义 |
| `@FeignClient` | `fetch('http://...')` | 调用其他微服务 |
| `@EnableDiscoveryClient` | 服务自注册到 Nacos | 让 Gateway 能找到它 |
| `SpringApplication.run()` | `app.listen(7101)` | 启动 HTTP 服务 |

---

## 端口速查表

| 端口 | 服务 | 用途 |
|------|------|------|
| 80 | Nginx | 统一入口（前端 + API） |
| 3000 | Next.js | 前端 SSR/CSR |
| 3306 | MySQL | 数据库 |
| 5672 | RabbitMQ | AMQP 协议 |
| 6379 | Redis | 缓存/会话 |
| 7091 / 8091 | Seata | 分布式事务 |
| 8070 | Sentinel | 限流控制台 |
| 7100 | Gateway | API 网关 |
| 7101 | 用户服务 | 注册/登录 |
| 7102 | 商品服务 | 商品/SKU/库存 |
| 7103 | 购物车服务 | 购物车 |
| 7104 | 订单服务 | 下单/秒杀 |
| 7105 | 支付服务 | 支付/退款 |
| 8848 | Nacos | 注册/配置中心 |
| 9200 | Elasticsearch | 商品搜索 |
| 15672 | RabbitMQ | 管理控制台 |

---

## 启动顺序图

```
  1. OrbStack 启动                    ← 手动打开 App
     │
  2. 基础设施 (make infra)            ← docker compose up
     ├── MySQL :3306
     ├── Redis :6379
     ├── Nacos :8848
     ├── RabbitMQ :5672
     ├── Elasticsearch :9200
     ├── Seata :8091
     └── Sentinel :8858(→localhost:8070)
     │
     ├── 等 15-30 秒就绪 ──┐
     │                     │
  3. 微服务 (make services)  ← Maven 编译 + Docker 构建
     ├── Gateway :7100     ← 依赖 Nacos + Redis
     ├── User :7101        ← 依赖 MySQL + Redis + Nacos
     ├── Product :7102     ← 依赖 MySQL + Redis + RabbitMQ + ES + Nacos
     ├── Cart :7103        ← 依赖 Redis + Nacos
     ├── Order :7104       ← 依赖 MySQL + Redis + RabbitMQ + Nacos
     └── Payment :7105     ← 依赖 MySQL + Redis + RabbitMQ + Nacos
     │
  4. 前端 (make frontend)
     └── Next.js :3000
     │
  5. Nginx :80                        ← 依赖 Gateway + Frontend
     │
  ✅ 全部就绪
     ├── http://localhost           → 电商首页
     ├── http://localhost/api        → API 网关
     ├── http://localhost:8848/nacos → Nacos 控制台
     ├── http://localhost:8070       → Sentinel 控制台
     └── http://localhost:15672      → RabbitMQ 控制台
```

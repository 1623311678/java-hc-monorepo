# Docker 运行架构：数据存在哪、服务跑在哪、怎么访问

> 14 个容器到底跑在什么地方？MySQL 数据会不会丢？容器之间怎么互相访问？这篇文档全部讲清楚。

---

## 一、整体架构：从你的 Mac 到容器

```
你的 MacBook (macOS)
│
│   你日常用的系统，浏览器、VS Code 都在这里
│
└── OrbStack (Docker 引擎)
    │
    │   OrbStack 在 macOS 上跑了一个轻量 Linux 内核
    │   所有容器共享这个内核，但互相隔离
    │
    └── Docker 网络 (hc-net)
        │
        ├── 容器: hc-mysql         ← 一个隔离的 Linux 进程
        ├── 容器: hc-redis         ← 一个隔离的 Linux 进程
        ├── 容器: hc-nacos         ← 一个隔离的 Linux 进程
        ├── 容器: hc-rabbitmq      ← 一个隔离的 Linux 进程
        ├── 容器: hc-elasticsearch  ← 一个隔离的 Linux 进程
        ├── 容器: hc-seata         ← 一个隔离的 Linux 进程
        ├── 容器: hc-sentinel      ← 一个隔离的 Linux 进程
        ├── 容器: hc-gateway       ← 一个隔离的 Linux 进程
        ├── 容器: hc-user          ← 一个隔离的 Linux 进程
        ├── 容器: hc-product       ← 一个隔离的 Linux 进程
        ├── 容器: hc-cart          ← 一个隔离的 Linux 进程
        ├── 容器: hc-order         ← 一个隔离的 Linux 进程
        ├── 容器: hc-payment       ← 一个隔离的 Linux 进程
        ├── 容器: hc-frontend      ← 一个隔离的 Linux 进程
        └── 容器: hc-nginx         ← 一个隔离的 Linux 进程
```

### 关键理解：Docker 不是虚拟机

| | 虚拟机 | Docker 容器 |
|---|---|---|
| 内核 | 每个虚拟机有自己的完整 OS 内核 | 所有容器共享宿主机内核 |
| 大小 | 几 GB（含完整 OS） | 几十 MB（只有应用 + 依赖） |
| 启动 | 几分钟 | 几秒钟 |
| 隔离 | 硬件级隔离 | 进程级隔离（namespace + cgroup） |
| 类比 | 在 Mac 里装了一台 Windows 电脑 | Chrome 里开了多个 Tab（独立进程，共享内核） |

> **Docker 容器 = 一个被隔离的 Linux 进程**，不是一台小电脑。

---

## 二、数据存在哪：Docker Volume

### 有状态服务 vs 无状态服务

```
有状态（数据要持久化，配了 Volume）:
  ✅ MySQL     → 数据库表、记录、索引
  ✅ Redis     → 持久化文件（RDB/AOF）
  ✅ Elasticsearch → 索引数据
  ✅ RabbitMQ  → 队列、消息
  ✅ Nacos     → 配置数据

无状态（没有 Volume，重启即丢失临时数据）:
  🔵 Nginx     → 只读配置，不存数据
  🔵 Gateway   → 只做路由转发
  🔵 微服务     → 业务逻辑，数据在 MySQL 里
  🔵 前端       → 静态页面，不存数据
  🔵 Sentinel  → 限流规则可丢，重启后重新加载
  🔵 Seata     → 事务日志，可重建
```

### Volume 的物理位置

Volume 数据存在 OrbStack 的虚拟磁盘里：

```
OrbStack 虚拟磁盘
└── /var/lib/docker/volumes/
    ├── java-hc-monorepo_mysql-data/_data/     ← MySQL 所有数据 (211 MB)
    │   ├── auto.cnf                            ← 服务器 UUID
    │   ├── ibdata1                            ← 系统表空间
    │   ├── hc_product/                        ← 商品数据库
    │   ├── hc_order/                          ← 订单数据库
    │   ├── hc_user/                           ← 用户数据库
    │   └── hc_payment/                        ← 支付数据库
    │
    ├── java-hc-monorepo_redis-data/_data/      ← Redis 持久化 (176 B)
    │   └── dump.rdb                           ← RDB 快照文件
    │
    ├── java-hc-monorepo_es-data/_data/         ← ES 索引 (33 KB)
    │   └── nodes/                             ← 节点数据
    │
    ├── java-hc-monorepo_rabbitmq-data/_data/   ← RabbitMQ (70 KB)
    │   ├── mnesia/                            ← 队列和交换机定义
    │   └── data/                              ← 消息存储
    │
    └── java-hc-monorepo_nacos-data/_data/      ← Nacos (3.5 MB)
        ├── data/                              ← 配置数据
        └── logs/                              ← 日志
```

### Volume 的生命周期

| 操作 | Volume 会怎样 | 数据还在吗 |
|------|-------------|----------|
| `docker compose down` | 保留 | ✅ 在 |
| `docker compose stop` | 保留 | ✅ 在 |
| `docker compose down -v` | **删除** | ❌ 丢了！ |
| `docker compose up -d` | 使用已有的 | ✅ 在 |
| 删容器重建 | 保留 | ✅ 在 |

> ⚠️ `docker compose down -v` 的 `-v` = 删除 Volume，MySQL 数据全没了！
> 普通停止用 `docker compose down`（不带 `-v`），数据安全。

### 前端类比

```
Docker Volume ≈ 浏览器 localStorage

docker compose down       → 关闭浏览器标签页，localStorage 还在
docker compose down -v    → 清除浏览器数据，localStorage 没了
docker compose up         → 重新打开标签页，localStorage 还能用
```

---

## 三、网络：容器怎么互相访问

### Docker 内部 DNS

Docker Compose 自动创建了一个内部网络 `hc-net`，所有容器都在这个网络里，可以通过**服务名**互相访问：

```
┌────────── Docker 内部网络 (hc-net) ──────────┐
│                                               │
│  Gateway ──→ nacos:8848     ✅ 服务名直达    │
│  hc-user ──→ mysql:3306     ✅ 服务名直达    │
│  hc-order ──→ redis:6379    ✅ 服务名直达    │
│  hc-order ──→ rabbitmq:5672 ✅ 服务名直达   │
│                                               │
└───────────────────────────────────────────────┘
```

### 为什么不用 IP？

```
❌ 用 IP（不推荐）
  Gateway 连 Nacos → 192.168.97.6:8848
  问题：容器重启后 IP 会变！下次就连不上了

✅ 用服务名（推荐）
  Gateway 连 Nacos → nacos:8848
  Docker 内部 DNS 自动把 "nacos" 解析成当前 IP
  容器重启后 IP 变了，但 "nacos" 名字不变，还能连
```

### 前端类比

```
Docker 服务名 ≈ 前端的环境变量 API_URL

// ❌ 硬编码 IP（相当于写死地址）
const API_URL = "http://192.168.1.100:3000/api"  // IP 变了就挂

// ✅ 用域名（相当于用服务名）
const API_URL = "http://api.myapp.com/api"       // 域名不变，IP 随便换

Docker 内部 DNS 就是自动帮你做 "nacos" → "192.168.97.6" 的域名解析
```

### Mac 本机 vs 容器内部：两种访问方式

```
你 Mac 的浏览器/终端:
  localhost:7100     ✅ 能访问 Gateway
  nacos:8848         ❌ 不认识这个域名！
  mysql:3306         ❌ 不认识！

容器内部（Java 代码里）:
  nacos:8848         ✅ Docker DNS 解析
  mysql:3306         ✅ Docker DNS 解析
  localhost:7100     ⚠️ 指向容器自己，不是你的 Mac！
```

---

## 四、端口映射：你的 Mac 怎么访问

OrbStack 自动把容器端口映射到 Mac 的 localhost：

### 业务服务端口

| 地址 | 服务 | 说明 |
|------|------|------|
| `http://localhost:80` | Nginx | 反向代理入口（生产环境唯一对外暴露的端口） |
| `http://localhost:3000` | Next.js 前端 | 前端开发页面 |
| `http://localhost:7100` | Gateway | API 网关（直接调微服务 API） |
| `http://localhost:7101` | hc-user | 用户服务（一般不直接访问，走 Gateway） |
| `http://localhost:7102` | hc-product | 商品服务 |
| `http://localhost:7103` | hc-cart | 购物车服务 |
| `http://localhost:7104` | hc-order | 订单服务 |
| `http://localhost:7105` | hc-payment | 支付服务 |

### 基础设施端口

| 地址 | 服务 | 说明 |
|------|------|------|
| `localhost:3306` | MySQL | 数据库（用 Navicat/DBeaver/MySQL Workbench 连接） |
| `localhost:6379` | Redis | 缓存（用 redis-cli 或 Another Redis Desktop Manager 连接） |
| `http://localhost:8848/nacos` | Nacos | 注册中心控制台（账号 nacos/nacos） |
| `http://localhost:15672` | RabbitMQ | 管理控制台（账号 admin/admin123） |
| `http://localhost:9200` | Elasticsearch | 搜索引擎 REST API |
| `http://localhost:7091` | Seata | 分布式事务控制台 |
| `http://localhost:8070` | Sentinel | 限流控制台（容器内监听 8858，映射到 Mac 的 8070） |

### ⚠️ 浏览器能访问 vs 不能访问

**浏览器只能访问 HTTP 协议的服务**，数据库和缓存用的是私有协议，浏览器不认识：

| 地址 | 浏览器 | 访问方式 |
|------|--------|---------|
| `http://localhost:3000` | ✅ | 前端页面 |
| `http://localhost:80` | ✅ | Nginx |
| `http://localhost:7100` | ✅ | Gateway API |
| `http://localhost:8070` | ✅ | Sentinel 控制台（账号 sentinel/sentinel） |
| `http://localhost:8848/nacos` | ✅ | Nacos 控制台（账号 nacos/nacos） |
| `http://localhost:15672` | ✅ | RabbitMQ 控制台（账号 admin/admin123） |
| `http://localhost:9200` | ✅ | Elasticsearch REST API |
| `http://localhost:7091` | ✅ | Seata 控制台 |
| `localhost:3306` | ❌ **不是 HTTP** | MySQL 协议 → 用 Navicat/DBeaver/MySQL Workbench 连 |
| `localhost:6379` | ❌ **不是 HTTP** | Redis 协议 → 用 redis-cli 或 Redis GUI 连 |

> 类比：浏览器只能看网页（HTTP），打不开 `ssh://`、`ftp://`、`mysql://`，因为这些不是 HTTP 协议。

### 请求链路

```
用户浏览器请求 "查看商品列表"

路径 1: 通过 Nginx（生产方式）
  浏览器 → localhost:80 → Nginx → localhost:7100 → Gateway → hc-product:7102

路径 2: 直接走 Gateway（开发调试）
  浏览器 → localhost:7100 → Gateway → hc-product:7102

路径 3: 直连微服务（仅调试用）
  浏览器 → localhost:7102 → hc-product
```

---

## 五、Docker 镜像存在哪

```
OrbStack 虚拟磁盘
└── /var/lib/docker/
    ├── overlay2/         ← 镜像层存储（所有 Docker 镜像）
    │   ├── maven:3.9-eclipse-temurin-21     (~400 MB)
    │   ├── eclipse-temurin:21-jre-alpine    (~170 MB)
    │   ├── mysql:8.0                        (~550 MB)
    │   ├── redis:7-alpine                   (~30 MB)
    │   ├── nacos/nacos-server:v2.3.2        (~1.2 GB)
    │   ├── rabbitmq:3.13-management-alpine  (~280 MB)
    │   ├── elasticsearch:8.13.4             (~1.3 GB)
    │   └── ... 每个微服务镜像 (~200 MB each)
    │
    └── volumes/             ← 数据卷（见上文）
```

查看磁盘占用：
```bash
docker system df            # 总览
docker system df -v         # 详细列表
```

---

## 六、日常操作速查

### 查看状态
```bash
docker compose ps                    # 看所有容器状态
docker compose ps gateway            # 看单个服务
docker compose logs gateway --tail=30 # 看日志
docker compose logs -f --tail=50     # 实时看所有日志
```

### 连接数据库
```bash
# MySQL（在 Mac 终端直连）
docker compose exec mysql mysql -uroot -proot

# Redis
docker compose exec redis redis-cli

# 或者用 GUI 工具连 localhost:3306 / localhost:6379
```

### 进入容器内部
```bash
docker compose exec gateway sh       # 进入 Gateway 容器的 shell
docker compose exec mysql bash       # 进入 MySQL 容器
```

### 清理空间
```bash
docker system prune                 # 清理未使用的容器、网络、镜像
docker system prune -a              # 清理所有未使用的镜像（慎用！下次要重新下载）
docker builder prune                # 清理构建缓存
```

---

## 七、你 Mac vs 生产环境对比

| 维度 | 你的 Mac | 生产环境 |
|------|---------|---------|
| 容器运行位置 | OrbStack 虚拟机里的 Linux 内核 | 云服务器上的 Linux 内核 |
| 数据存储 | Docker Volume（虚拟磁盘） | 云磁盘（AWS EBS / 阿里云云盘） |
| 数据库 | Docker 里的 MySQL | 云 RDS（自动备份、主从同步） |
| Redis | Docker 里的 Redis | 云 Redis（自动集群、持久化） |
| 网络 | Docker 内部 DNS | Kubernetes Service / 云 VPC |
| 端口暴露 | localhost:xxx | 域名 + HTTPS (443) |
| 配置管理 | application.yml 文件 | Nacos 配置中心 |
| 部署方式 | docker compose up | K8s / 云平台滚动发布 |
| 监控 | docker compose logs | Prometheus + Grafana |
| 日志 | 终端输出 | ELK (Elasticsearch + Logstash + Kibana) |

> 核心结论：**架构一样，运维方式不同**。你的代码从 Mac 迁到云上不需要改，只需要换基础设施的地址和运维方式。

# Docker Compose 日常使用指南

> 面向：前端转全栈开发者  
> 目标：掌握 Docker Compose 的日常使用，理解它解决的核心问题

---

## 目录

1. [Docker Compose 是什么？解决什么问题？](#1-docker-compose-是什么解决什么问题)
2. [核心概念速查](#2-核心概念速查)
3. [日常命令手册](#3-日常命令手册)
4. [典型工作流：你的一天](#4-典型工作流你的一天)
5. [两种开发模式对比](#5-两种开发模式对比)
6. [docker-compose.yml 文件解读](#6-docker-composeyml-文件解读)
7. [数据卷（volumes）详解](#7-数据卷volumes详解)
8. [网络（networks）详解](#8-网络networks详解)
9. [常见问题排查](#9-常见问题排查)
10. [前端类比对照表](#10-前端类比对照表)

---

## 1. Docker Compose 是什么？解决什么问题？

### 一句话定义

**Docker Compose 是"一条命令把整个后端环境拉起来"的工具。**

它读一个 `docker-compose.yml` 文件，知道要启动哪些容器、怎么配置、怎么互连，然后一口气全部跑起来。

### 没有它 vs 有它

```
❌ 没有 Docker Compose，你需要手动：
┌──────────────────────────────────────────────┐
│ 1. 下载安装 MySQL 8.0，配置用户密码，建库建表    │
│ 2. 下载安装 Redis 7，配置内存策略               │
│ 3. 下载安装 Nacos 2.3，启动注册中心             │
│ 4. 下载安装 RabbitMQ 3.13，配置管理员账号        │
│ 5. 下载安装 Elasticsearch 8.13，关掉安全认证    │
│ 6. 下载安装 Seata 1.7，配置注册到 Nacos         │
│ 7. 下载安装 Sentinel 1.8                       │
│ 8. 配置 Nginx 反向代理                         │
│ 9. 确保所有端口不冲突                           │
│ 10. 确保所有服务互相能访问                       │
│                                               │
│ 👉 全程 2-4 小时，容易配错，换台电脑又要重来一遍   │
└──────────────────────────────────────────────┘

✅ 有 Docker Compose，你只需要：
┌──────────────────────────────────────────────┐
│ docker compose up -d                          │
│                                               │
│ 👉 5 分钟后全部就绪，配置一模一样               │
└──────────────────────────────────────────────┘
```

### 解决的 3 个核心问题

| 问题 | 痛点 | Docker Compose 怎么解决 |
|------|------|----------------------|
| **环境不一致** | "我本地能跑，你跑不起来" | 同一个 YAML = 同一个环境，MySQL 版本、配置都一样 |
| **安装繁琐** | 装 MySQL/Redis/Nacos 要半天 | 不用装，全跑在容器里，删了也不脏系统 |
| **配置关联复杂** | 7 个服务互相依赖，手动配容易错 | `depends_on` + `networks` 自动处理依赖和互通 |

---

## 2. 核心概念速查

| 概念 | 含义 | 前端类比 |
|------|------|---------|
| **Image（镜像）** | 容器的"安装包"，只读 | `npm install` 下载的包 |
| **Container（容器）** | 镜像运行起来的实例 | `node server.js` 启动的进程 |
| **Volume（数据卷）** | 容器外挂的硬盘，数据持久化 | `localStorage` |
| **Network（网络）** | 容器间的通信网络 | 同一局域网 |
| **Service（服务）** | YAML 中定义的一个容器 | `package.json` 中的一个脚本 |
| **Build（构建）** | 从源码打包成镜像 | `npm run build` |
| **Environment（环境变量）** | 传给容器的配置 | `.env` 文件 |
| **Port Mapping（端口映射）** | 宿主机端口 → 容器端口 | Nginx 反向代理端口转发 |
| **depends_on（依赖）** | 启动顺序 | `await db.connect()` |

### 容器 vs 镜像 vs 数据卷 的关系

```
镜像 (Image)          = 安装包（只读，不可修改）
   │
   │  docker compose up -d
   ▼
容器 (Container)      = 运行中的程序（可读写，但删了就没了）
   │
   │  挂载 Volume
   ▼
数据卷 (Volume)       = 外挂硬盘（删容器不丢数据）
```

---

## 3. 日常命令手册

### 🔥 最常用的 3 条命令

```bash
# 1. 启动（后台运行）
docker compose up -d [服务名...]

# 2. 停止
docker compose down

# 3. 查看状态
docker compose ps
```

### 完整命令表

#### 启动与停止

| 命令 | 作用 | 说明 |
|------|------|------|
| `docker compose up -d` | 启动所有容器 | `-d` = 后台运行（detached） |
| `docker compose up -d mysql redis` | 只启动指定服务 | 日常开发只跑基础设施 |
| `docker compose stop` | 暂停所有容器 | 容器还在，可 `start` 恢复 |
| `docker compose start` | 恢复暂停的容器 | 比 `up` 快，不需要重新创建 |
| `docker compose down` | 停止并删除容器 | **数据卷保留**，下次 up 自动恢复 |
| `docker compose down -v` | 停止并删除容器 + 数据卷 | ⚠️ **数据库数据全丢！** |
| `docker compose restart hc-product` | 重启某个服务 | 改了配置后用 |

#### 查看与调试

| 命令 | 作用 |
|------|------|
| `docker compose ps` | 查看运行状态（Up / Exited / Restarting） |
| `docker compose ps -a` | 包括已停止的容器 |
| `docker compose logs` | 查看所有服务日志 |
| `docker compose logs -f gateway` | 实时跟踪 gateway 日志（Ctrl+C 退出） |
| `docker compose logs --tail=50 hc-order` | 最近 50 行 order 日志 |
| `docker compose top` | 查看容器内进程（类似 top 命令） |

#### 进入容器

| 命令 | 作用 |
|------|------|
| `docker compose exec mysql mysql -uroot -proot` | 进入 MySQL 命令行 |
| `docker compose exec redis redis-cli` | 进入 Redis 命令行 |
| `docker compose exec rabbitmq rabbitmqctl list_queues` | 查看 RabbitMQ 队列 |
| `docker compose exec hc-product sh` | 进入商品服务容器 shell |

#### 构建镜像

| 命令 | 作用 | 什么时候用 |
|------|------|-----------|
| `docker compose build` | 重新构建所有微服务镜像 | 改了 Java 代码后 |
| `docker compose build hc-order` | 只构建订单服务 | 改了某个服务代码后 |
| `docker compose up -d --build hc-order` | 构建并重启订单服务 | 最常用：改代码后一键生效 |
| `docker compose up -d --force-recreate hc-order` | 强制重建容器 | 配置变了构建缓存不生效时 |

#### 数据操作

| 命令 | 作用 |
|------|------|
| `make reset-db` | 重置数据库（删卷重建，执行 init.sql） |
| `make backup` | 备份数据库到 SQL 文件 |
| `make shell-mysql` | 进入 MySQL 命令行 |
| `make shell-redis` | 进入 Redis 命令行 |

#### 清理

| 命令 | 作用 | 危险程度 |
|------|------|---------|
| `docker system prune -f` | 清理未使用的镜像/容器/缓存 | 🟡 安全，不影响运行中的容器 |
| `docker system prune -a` | 清理所有未使用的镜像 | 🟠 下次构建需重新拉取镜像 |
| `make clean` | 停止所有 + 删卷 + 清理 | 🔴 数据全丢！ |

---

## 4. 典型工作流：你的一天

```
09:00  ─ 上班，开电脑 ────────────────────────────────
       │
       ▼
       $ docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel
       基础设施 30 秒就绪 ✅
       │
       ▼
       在 IDE 里跑微服务（本地开发模式）
       $ cd services/user && mvn spring-boot:run
       改代码 → 保存 → 自动热重载 → 秒级反馈 ✅
       │
12:00  ─ 午饭 ──────────────────────────────────────
       容器继续跑，不用管 ✅
       │
14:00  ─ 改完 User 服务代码，要全链路测试 ──────────
       │
       ▼
       $ docker compose up -d --build hc-user
       重新构建用户服务镜像 + 重启 ✅
       │
       ▼
       跑一遍完整流程：注册 → 登录 → 浏览商品 → 加购物车 → 下单
       │
16:00  ─ 改了 Nacos 配置，需要重启 ──────────────────
       │
       ▼
       $ docker compose restart hc-product hc-order
       │
18:00  ─ 下班 ──────────────────────────────────────
       │
       ▼
       $ docker compose down
       容器停了，数据在卷里保留，明天接着用 ✅
```

---

## 5. 两种开发模式对比

### 模式 A：日常开发（推荐 80% 时间）

```bash
# 只用 Docker 跑基础设施
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel

# 微服务在 IDE 里跑（快！改代码秒生效）
# IntelliJ IDEA: 点 Run 按钮
# 或终端：
cd services/user && mvn spring-boot:run
cd services/product && mvn spring-boot:run
```

| 优点 | 说明 |
|------|------|
| ⚡ 快 | 启动 10-30 秒，改代码自动热重载 |
| 🐛 好调试 | IDE 断点、变量查看、单步执行 |
| 📝 日志清晰 | 直接在终端看，不用 docker compose logs |

### 模式 B：全链路联调（20% 时间）

```bash
# 微服务也跑在 Docker 里
make dev
# 或：
docker compose up -d
```

| 优点 | 说明 |
|------|------|
| 🔄 完整 | 全链路：Nginx → Gateway → 微服务 → DB |
| 🎯 真实 | 跟生产环境一样，Docker 内通信 |
| 🧪 适合验证 | 端口映射、Nginx 限流、Gateway 路由等 |

### 对比表

| | Docker 部署（模式 B） | 本地开发（模式 A） |
|---|-----------|---------|
| 启动速度 | 5-10 分钟（首次） | 10-30 秒 |
| 改代码后 | 重新构建镜像（分钟级） | 自动热重载（秒级） |
| 断点调试 | 很难 | 随便打断点 |
| 看日志 | `docker compose logs` | 直接在终端看 |
| 端口访问 | 通过 Nginx/Gateway | 直连 localhost:7101 |
| 用途 | 联调 / 演示 / 验证 | **日常写代码** |

---

## 6. docker-compose.yml 文件解读

本项目定义了 **14 个服务**，分 3 层：

```
docker-compose.yml
│
├── 7 个基础设施（直接拉镜像，秒启动）
│   ├── nacos        注册/配置中心
│   ├── sentinel     限流控制台
│   ├── mysql        数据库
│   ├── redis        缓存
│   ├── rabbitmq     消息队列
│   ├── elasticsearch 搜索引擎
│   └── seata        分布式事务
│
├── 6 个微服务（需要 Maven 构建，首次较慢）
│   ├── gateway       API 网关
│   ├── hc-user       用户服务
│   ├── hc-product    商品服务
│   ├── hc-cart       购物车服务
│   ├── hc-order      订单服务
│   └── hc-payment    支付服务
│
└── 1 个前端
    └── frontend      Next.js 电商页面
```

### 每个服务的关键配置项

以 MySQL 为例，逐行解读：

```yaml
mysql:                                    # 服务名（其他容器用 "mysql" 访问它）
  image: mysql:8.0                        # 从 Docker Hub 拉哪个镜像
  container_name: hc-mysql                # 容器名（docker ps 里显示的名字）
  environment:                            # 环境变量（= .env 里的配置）
    MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD:-root}  # 读 .env，没有则用默认值 root
    MYSQL_DATABASE: hc_product            # 首次启动自动创建此数据库
    TZ: Asia/Shanghai                     # 时区
  ports:
    - "3306:3306"                         # 端口映射：宿主机3306 → 容器3306
                                            # 你用 Navicat 连 localhost:3306 就是连它
  volumes:
    - mysql-data:/var/lib/mysql           # 数据持久化到 docker 卷
    - ./deploy/docker/mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
                                            # 挂载初始化脚本（首次启动自动执行）
  command: >                              # 覆盖默认启动命令，加自定义参数
    --character-set-server=utf8mb4
    --collation-server=utf8mb4_unicode_ci
    --innodb-buffer-pool-size=256M
    --max-connections=500
  restart: always                         # 崩溃自动重启
  networks:
    - hc-net                              # 加入 hc-net 网络
```

### 两种服务类型的区别

| | 基础设施 | 微服务 |
|---|---------|--------|
| 启动方式 | `image: mysql:8.0` | `build: { context, dockerfile }` |
| 来源 | Docker Hub 直接拉 | 本地源码 → Maven 编译 → Docker 镜像 |
| 首次启动 | 几秒（拉完镜像即可） | 5-10 分钟（需编译） |
| 后续启动 | 几秒 | 几十秒（有缓存） |
| 改代码后 | 不需要重建 | 需要 `docker compose build xxx` |

### 端口映射规则

```
格式: "宿主机端口:容器端口"

"3306:3306"  →  你访问 localhost:3306  →  转发到容器内 3306
"8080:7100"  →  你访问 localhost:7100  →  转发到容器内 8080

⚠️ 如果宿主机端口被占用，可以改映射：
"13306:3306" →  你访问 localhost:13306  →  转发到容器内 3306
```

---

## 7. 数据卷（volumes）详解

### 为什么需要 Volume？

```
没有 Volume:                              有 Volume:
┌──────────────┐                         ┌──────────────┐
│   容器       │                         │   容器       │
│  /var/lib/   │                         │  /var/lib/   │
│   mysql/     │                         │   mysql/ ──────── 挂载 ──── 数据卷
│              │                         │              │              (宿主机磁盘)
│  数据在容器内 │                         │              │         ┌──────────────┐
│  删容器=丢数据│                         │              │         │ mysql-data/  │
└──────────────┘                         └──────────────┘         │  ibdata1     │
                                                                  │  hc_product/ │
  ❌ docker compose down                  ✅ docker compose down   └──────────────┘
     数据全丢！                              容器删了，数据还在！
```

### 本项目的 5 个数据卷

| 卷名 | 存什么 | 删了的后果 |
|------|--------|-----------|
| `mysql-data` | 所有数据库文件 | 所有表和数据全丢，下次启动重新执行 init.sql（只有测试数据） |
| `redis-data` | Redis AOF 持久化文件 | 缓存全丢（影响不大，缓存本来就可以重建） |
| `rabbitmq-data` | RabbitMQ 队列和消息 | 未消费的消息丢失 |
| `es-data` | Elasticsearch 索引 | 搜索索引全丢，需要重新同步 |
| `nacos-data` | Nacos 注册和配置数据 | 服务注册信息和配置中心数据丢失 |

### 卷的操作

```bash
# 查看所有卷
docker volume ls

# 查看某个卷的详细信息
docker volume inspect java-hc-monorepo_mysql-data

# 删除某个卷（⚠️ 数据丢失）
docker volume rm java-hc-monorepo_mysql-data

# 删除所有未使用的卷
docker volume prune
```

---

## 8. 网络（networks）详解

### 为什么需要网络？

所有 14 个容器都加入 `hc-net` 桥接网络，这意味着：

**容器之间用"服务名"当主机名互访，不需要知道 IP。**

```
┌──────────────── hc-net 网络 ────────────────┐
│                                             │
│  hc-product ──→ mysql:3306     ✅ 用服务名   │
│  hc-order   ──→ redis:6379    ✅ 用服务名   │
│  gateway    ──→ hc-user:7101  ✅ 用服务名   │
│  nginx      ──→ gateway:7100  ✅ 用服务名   │
│                                             │
│  Docker 内部 DNS 自动把 "mysql" 解析为容器 IP │
└─────────────────────────────────────────────┘
```

### 对比：宿主机 vs 容器内访问

| 从哪访问 | MySQL 地址 | Redis 地址 |
|---------|-----------|-----------|
| **容器内**（另一个容器） | `mysql:3306` | `redis:6379` |
| **宿主机**（你的 Mac） | `localhost:3306` | `localhost:6379` |

> 这就是为什么微服务的 `application.yml` 里数据库地址是 `mysql:3306` 而不是 `localhost:3306`。

---

## 9. 常见问题排查

### Q1: 容器启动后立刻退出

```bash
# 查看退出日志
docker compose logs hc-product

# 常见原因：
# - Nacos 没启动完 → 多等一会儿
# - MySQL 没就绪 → 看 MySQL 日志是否出现 "ready for connections"
# - 端口冲突 → lsof -i :7102 检查
```

### Q2: 端口冲突

```bash
# 查看谁占用了端口
lsof -i :3306

# 解决方案 1：停掉占用端口的进程
kill -9 <PID>

# 解决方案 2：改 docker-compose.yml 的端口映射
ports:
  - "13306:3306"   # 改宿主机端口
```

### Q3: Apple Silicon 报 "no matching manifest for linux/arm64/v8"

```yaml
# 在该服务下加 platform 指定用 amd64 模拟
nacos:
  image: nacos/nacos-server:v2.3.2
  platform: linux/amd64    # ← 加这行
```

### Q4: Maven 构建极慢

首次需要下载依赖，约 3-5 分钟。可配置阿里云镜像加速：

```bash
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
  </mirrors>
</settings>
EOF
```

### Q5: Docker 磁盘空间不足

```bash
# 查看 Docker 磁盘使用
docker system df

# 清理未使用的镜像/容器/缓存（安全，不影响运行中的容器）
docker system prune -f

# 深度清理（⚠️ 下次构建需重新拉取镜像）
docker system prune -a
```

### Q6: 想完全重来

```bash
# 停止所有容器 + 删除所有数据卷 + 清理
docker compose down -v
docker system prune -f

# 重新开始
docker compose up -d
```

---

## 10. 前端类比对照表

| Docker Compose 概念 | 前端等价概念 | 说明 |
|-------------------|-------------|------|
| `docker compose up -d` | `npm run dev` | 启动项目 |
| `docker compose down` | Ctrl+C 关掉 dev server | 停止项目 |
| `docker compose down -v` | 清除 localStorage + 关掉 | 停止并清数据 |
| `docker compose ps` | 打开浏览器看页面有没有加载 | 检查状态 |
| `docker compose logs -f` | Chrome DevTools Console | 看日志 |
| `docker compose exec mysql ...` | 打开 DevTools → Application | 查看数据 |
| `docker compose build` | `npm run build` | 构建 |
| `image: mysql:8.0` | `"mysql": "^8.0"` in package.json | 指定版本 |
| `volumes` | `localStorage` | 数据持久化 |
| `networks` | 同一局域网 | 服务间通信 |
| `depends_on` | `await db.connect()` | 启动依赖 |
| `environment` | `.env.local` | 环境变量 |
| `ports: "3306:3306"` | `app.listen(3306)` | 端口映射 |
| `restart: always` | PM2 `autorestart` | 自动重启 |
| `docker-compose.yml` | `package.json` + `vercel.json` | 项目运行配置 |

---

## 端口速查表

| 端口 | 服务 | 用途 | 访问方式 |
|------|------|------|---------|
| 80 | Nginx | 统一入口 | http://localhost |
| 3000 | Next.js | 前端页面 | http://localhost:3000 |
| 3306 | MySQL | 数据库 | Navicat / `mysql -h localhost -P 3306` |
| 5672 | RabbitMQ | AMQP 协议 | 容器内互访 |
| 6379 | Redis | 缓存 | `redis-cli -h localhost -p 6379` |
| 7091/8091 | Seata | 分布式事务 | 容器内互访 |
| 8070 | Sentinel | 限流控制台 | http://localhost:8070 |
| 7100 | Gateway | API 网关 | http://localhost:7100 |
| 7101 | 用户服务 | 注册/登录 | 容器内互访 |
| 7102 | 商品服务 | 商品/SKU | http://localhost:7102/doc.html (API文档) |
| 7103 | 购物车服务 | 购物车 | 容器内互访 |
| 7104 | 订单服务 | 下单/秒杀 | 容器内互访 |
| 7105 | 支付服务 | 支付/退款 | 容器内互访 |
| 8848 | Nacos | 注册/配置中心 | http://localhost:8848/nacos |
| 9200 | Elasticsearch | 商品搜索 | http://localhost:9200 |
| 15672 | RabbitMQ | 管理控制台 | http://localhost:15672 |

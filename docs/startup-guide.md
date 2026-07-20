# 项目启动流程手册

> 每次打开电脑开发时的启动步骤，分场景指导

---

## 目录

1. [首次启动（从零开始）](#1-首次启动从零开始)
2. [日常启动（每天上班）](#2-日常启动每天上班)
3. [停止服务（下班关机）](#3-停止服务下班关机)
4. [改了代码后重启](#4-改了代码后重启)
5. [启动顺序与依赖关系](#5-启动顺序与依赖关系)
6. [启动后验证清单](#6-启动后验证清单)
7. [常见启动问题排查](#7-常见启动问题排查)

---

## 1. 首次启动（从零开始）

> 适用：刚克隆项目、换了新电脑、`docker compose down -v` 后重头开始

### 第一步：确保前置条件

```bash
# 检查 3 个必须的软件
docker --version          # 需要 Docker 29.x+
java -version             # 需要 OpenJDK 21.x
mvn -version              # 需要 Maven 3.9.x

# 确保 OrbStack 在运行（菜单栏有图标）
# 如果没有，手动打开 OrbStack 应用
```

### 第二步：创建环境变量

```bash
cd ~/java-hc-monorepo
cp .env.example .env
# 本地开发用默认密码即可，不用改
```

### 第三步：启动基础设施

```bash
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel
```

⏳ 首次需要拉取镜像，约 5-10 分钟。后续秒启动。

等容器就绪（约 15-30 秒）：
```bash
docker compose ps
# 确认 STATUS 全部是 Up
```

### 第四步：构建并启动微服务

```bash
docker compose up -d --build gateway hc-user hc-product hc-cart hc-order hc-payment
```

⏳ 首次构建需要 Maven 下载依赖，约 5-10 分钟。后续有缓存，几十秒。

### 第五步：启动前端 + Nginx

```bash
docker compose up -d --build frontend
docker compose up -d nginx
```

### 第六步：验证

打开浏览器访问：
- http://localhost — 电商首页
- http://localhost:8848/nacos — Nacos 控制台

---

## 2. 日常启动（每天上班）

> 适用：昨天下班 `docker compose down` 了，今天重新启动

### 方式 A：一键全启动

```bash
cd ~/java-hc-monorepo
make dev
```

等 15-30 秒全部就绪。

### 方式 B：分步启动（推荐，更可控）

```bash
cd ~/java-hc-monorepo

# 1. 基础设施（秒启动，镜像已下载）
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel

# 2. 等 15 秒让基础设施就绪
sleep 15

# 3. 微服务（有缓存，几十秒启动）
docker compose up -d gateway hc-user hc-product hc-cart hc-order hc-payment

# 4. 前端 + Nginx
docker compose up -d frontend nginx

# 5. 确认全部 Up
docker compose ps
```

### 方式 C：只启动基础设施（日常开发推荐）

> 微服务在 IDE 里跑，改代码秒生效

```bash
cd ~/java-hc-monorepo

# 只启动数据库/缓存/消息队列等
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel

# 然后在 IDE 或终端跑微服务：
cd services/user && mvn spring-boot:run
cd services/product && mvn spring-boot:run
# ... 其他服务
```

### 三种方式对比

| | 方式 A 一键全启动 | 方式 B 分步启动 | 方式 C 只跑基础设施 |
|---|-----------|-------------|---------------|
| 速度 | 中等 | 中等 | 最快 |
| 用途 | 全链路联调 | 全链路联调 | **日常开发** |
| 微服务在哪跑 | Docker 容器 | Docker 容器 | IDE 本地 |
| 改代码后 | 需重新构建 | 需重新构建 | **秒生效** |
| 推荐频率 | 20% 时间 | 20% 时间 | **80% 时间** |

---

## 3. 停止服务（下班关机）

### 停止但保留数据（推荐）

```bash
docker compose down
```

> 容器停了，但 MySQL/Redis/Nacos 的数据保存在 Docker 卷里。  
> 明天 `docker compose up -d` 就能恢复。

### 停止并清空数据（慎用）

```bash
docker compose down -v
```

> ⚠️ 所有数据库、缓存、队列数据全部删除！  
> 下次启动 MySQL 会重新执行 `init.sql`（只有测试数据）。

### 只暂停，不删容器

```bash
docker compose stop       # 暂停所有
docker compose stop hc-order  # 只暂停订单服务
```

> `stop` = 容器还在，下次 `start` 即可恢复。比 `down` 快。

| 命令 | 容器 | 数据 | 恢复速度 |
|------|------|------|---------|
| `docker compose stop` | 保留（暂停） | 保留 | 最快（秒级） |
| `docker compose down` | 删除 | **保留** | 快（十几秒） |
| `docker compose down -v` | 删除 | **删除！** | 慢（需重新构建） |

---

## 4. 改了代码后重启

### 场景 A：改了 Java 微服务代码（Docker 模式跑的）

```bash
# 重新构建 + 重启某个服务
docker compose up -d --build hc-product

# 或者分两步
docker compose build hc-product
docker compose up -d hc-product
```

### 场景 B：改了前端代码（Docker 模式跑的）

```bash
docker compose up -d --build frontend
```

### 场景 C：改了 Java 微服务代码（IDE 本地跑的）

> 不需要任何 Docker 操作！保存文件后 IDE 自动热重载。

### 场景 D：改了基础设施配置（Nginx/Nacos/Seata）

```bash
# 改了 Nginx 配置
docker compose restart nginx

# 改了 Nacos 配置（在控制台改的会自动生效，不用重启）
# 改了 docker-compose.yml 里的配置
docker compose up -d   # 自动检测变化并重建
```

---

## 5. 启动顺序与依赖关系

### 必须按顺序启动！

```
第一层：基础设施（无依赖，可同时启动）
┌─────────────────────────────────────────────────┐
│  mysql → redis → nacos → rabbitmq → es → seata │
│  → sentinel                                      │
└─────────────────────────────────────────────────┘
       │
       │ 等 15-30 秒让服务就绪
       ▼
第二层：微服务（依赖基础设施）
┌─────────────────────────────────────────────────┐
│  gateway (依赖 nacos + redis)                    │
│  hc-user (依赖 mysql + redis + nacos)            │
│  hc-product (依赖 mysql + redis + rabbitmq +    │
│              es + nacos)                         │
│  hc-cart (依赖 redis + nacos)                    │
│  hc-order (依赖 mysql + redis + rabbitmq +       │
│            nacos)                                │
│  hc-payment (依赖 mysql + redis + rabbitmq +     │
│              nacos)                              │
└─────────────────────────────────────────────────┘
       │
       │ 等微服务注册到 Nacos（约 10 秒）
       ▼
第三层：接入层（依赖微服务 + 前端）
┌─────────────────────────────────────────────────┐
│  nginx (依赖 gateway + frontend)                 │
└─────────────────────────────────────────────────┘
```

### 为什么必须等？

```
❌ 不等就启动微服务：
  微服务连不上 Nacos → 注册失败 → 启动报错
  微服务连不上 MySQL → 数据库连接失败 → 启动报错

✅ 等基础设施就绪后启动微服务：
  Nacos ready → 微服务注册成功 → Gateway 能找到微服务
  MySQL ready → 微服务建连成功 → 业务正常
```

### 怎么判断基础设施就绪？

```bash
# 看 MySQL 是否就绪
docker compose logs mysql 2>&1 | grep "ready for connections"
# 出现 2 次 "ready for connections" 说明 MySQL 已就绪

# 看 Nacos 是否就绪
curl -s http://localhost:8848/nacos/ | head -1
# 有返回就说明 Nacos 就绪

# 看 Redis 是否就绪
docker compose exec redis redis-cli ping
# 返回 PONG 就说明 Redis 就绪

# 简单粗暴：等 15-30 秒，一般都够了
```

---

## 6. 启动后验证清单

### 基础设施验证

```bash
# MySQL
docker compose exec mysql mysql -uroot -proot -e "SHOW DATABASES;"
# 期望看到: hc_product, hc_order, hc_user, hc_payment

# Redis
docker compose exec redis redis-cli ping
# 期望: PONG

# Nacos
curl -s http://localhost:8848/nacos/ | head -1
# 期望有 HTML 返回

# Elasticsearch
curl http://localhost:9200
# 期望返回 ES 版本信息 JSON

# RabbitMQ
curl -u admin:admin123 http://localhost:15672/api/overview
# 期望返回 RabbitMQ 状态 JSON
```

### 微服务验证

```bash
# 查看所有服务是否 Up
docker compose ps

# 查看 Nacos 注册的服务
curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20" | python3 -m json.tool
# 期望看到: hc-gateway, hc-user, hc-product, hc-cart, hc-order, hc-payment

# 通过 Gateway 访问
curl http://localhost:7100/product/list
curl http://localhost:7100/user/list
```

### 前端验证

```bash
# 访问首页
curl -s http://localhost:3000 | head -5
# 期望返回 HTML

# 通过 Nginx 访问
curl -s http://localhost | head -5
# 期望返回 HTML
```

### 控制台访问

| URL | 账号/密码 | 检查内容 |
|-----|----------|---------|
| http://localhost:8848/nacos | nacos/nacos | 服务列表是否有 6 个微服务 |
| http://localhost:8070 | sentinel/sentinel | 限流规则是否加载 |
| http://localhost:15672 | admin/admin123 | 队列是否创建 |
| http://localhost:9200 | — | ES 是否响应 |
| http://localhost | — | 电商首页是否正常 |

---

## 7. 常见启动问题排查

### Q1: 微服务启动后立刻退出

```bash
# 查看退出日志
docker compose logs hc-product --tail=50
```

常见原因与解决：

| 日志关键信息 | 原因 | 解决 |
|------------|------|------|
| `Connection refused: nacos:8848` | Nacos 还没就绪 | 等 15 秒后再 `docker compose up -d hc-product` |
| `Access denied for user` | MySQL 密码不对 | 检查 `.env` 中 `MYSQL_PASSWORD` |
| `Port 7102 already in use` | 端口冲突 | `lsof -i :7102` 找到占用进程 kill 掉 |
| `Table 'hc_order.t_order' doesn't exist` | 数据库没初始化 | 重新执行 init.sql（见 Q3） |

### Q2: Nginx 启动失败

```bash
# 常见原因: gateway 或 frontend 还没启动
docker compose logs nginx --tail=20

# 解决: 确保 gateway 和 frontend 都 Up 了
docker compose ps gateway frontend
docker compose up -d nginx
```

### Q3: 数据库需要重新初始化

```bash
# 方式 1: 用 Makefile
make reset-db

# 方式 2: 手动
docker compose down -v   # 删容器 + 数据卷
docker compose up -d mysql  # 重新启动 MySQL，自动执行 init.sql
sleep 10
docker compose up -d    # 启动其他服务
```

### Q4: 全部重来

```bash
# 停掉一切 + 清理
docker compose down -v
docker builder prune -f

# 从头开始
docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel
sleep 15
docker compose up -d --build gateway hc-user hc-product hc-cart hc-order hc-payment
docker compose up -d --build frontend
docker compose up -d nginx
```

### Q5: 构建卡住不动

```bash
# 如果 Maven 下载依赖卡住，可以 Ctrl+C 中断，然后重试
# 有阿里云镜像加持，通常不会卡太久

# 如果确实很慢，检查网络
ping maven.aliyun.com

# 强制重新构建（不用缓存）
docker compose build --no-cache hc-product
```

---

## 快速启动命令速查卡

```
┌─────────────────────────────────────────────────────┐
│                每天上班                               │
│                                                     │
│  docker compose up -d    # 一键全启动                 │
│  docker compose ps       # 检查状态                   │
│                                                     │
├─────────────────────────────────────────────────────┤
│                日常开发                               │
│                                                     │
│  docker compose up -d mysql redis nacos              │
│                    rabbitmq elasticsearch seata      │
│                    sentinel                           │
│  # 然后在 IDE 里跑微服务                               │
│                                                     │
├─────────────────────────────────────────────────────┤
│                改了代码                               │
│                                                     │
│  docker compose up -d --build hc-product  # 重建某个  │
│  docker compose up -d --build frontend     # 重建前端  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                下班关机                               │
│                                                     │
│  docker compose down     # 停止（数据保留）            │
│  docker compose stop     # 暂停（容器保留）            │
│                                                     │
├─────────────────────────────────────────────────────┤
│                排查问题                               │
│                                                     │
│  docker compose ps                    # 看状态        │
│  docker compose logs -f hc-order     # 看日志        │
│  docker compose exec mysql mysql ... # 查数据库       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

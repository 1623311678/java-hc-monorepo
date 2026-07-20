# 踩坑实录：java-hc-monorepo 部署全记录

> 从零到 14 个容器全部跑起来，我踩了 18 个坑。这份文档记录了每一个坑的现象、根因和修复方式。
> 适合：前端转后端 / 首次接触 Docker + Java 微服务 / Apple Silicon Mac 用户

---

## 你现在的状态

没错，你的 Mac 现在就是一台单机版服务器，跑了 14 个容器：

```
你的 MacBook = 一台服务器
├── 基础设施 (7 个)
│   ├── MySQL 8        :3306    ← 数据库
│   ├── Redis 7        :6379    ← 缓存
│   ├── Nacos 2.3      :8848    ← 注册中心 + 配置中心
│   ├── RabbitMQ 3.13  :5672    ← 消息队列
│   ├── Elasticsearch  :9200    ← 搜索引擎
│   ├── Seata 1.7     :7091    ← 分布式事务
│   └── Sentinel 1.8   :8070    ← 限流
├── 微服务 (6 个)
│   ├── Gateway        :7100    ← API 网关
│   ├── hc-user        :7101    ← 用户服务
│   ├── hc-product     :7102    ← 商品服务
│   ├── hc-cart        :7103    ← 购物车服务
│   ├── hc-order       :7104    ← 订单服务
│   └── hc-payment     :7105    ← 支付服务
└── 前端 + 网关代理 (2 个)
    ├── Next.js        :3000    ← 前端页面
    └── Nginx          :80      ← 反向代理 + 限流
```

这和生产环境架构完全一样，只是从多台服务器缩到了你一台电脑。

---

## 坑 1：OrbStack 装了但 docker 命令找不到

### 现象
```bash
➜ docker --version
zsh: command not found: docker
```

### 根因
OrbStack 安装后不会自动启动，需要手动打开 App 才会创建 `/usr/local/bin/docker` 符号链接。

### 修复
```bash
open -a OrbStack    # 启动 App，自动创建 docker 命令
```

### 前端类比
类似装了 Node.js 但没开终端重载 `.zshrc`，`npm` 命令不生效。

---

## 坑 2：Nacos 镜像没有 ARM64 版本

### 现象
```
no matching manifest for linux/arm64/v8
```

### 根因
Apple Silicon (M 系列) 是 ARM64 架构，而 Nacos 官方 Docker 镜像只提供了 amd64 版本。

### 修复
在 `docker-compose.yml` 中给 Nacos 加平台声明：
```yaml
nacos:
  platform: linux/amd64    # ← 强制用 amd64 镜像（通过 QEMU 模拟运行）
  image: nacos/nacos-server:v2.3.2
```

### 前端类比
类似某些 npm 包只提供 x64 的 native binding，Apple Silicon 上需要加 `--target=arm64` 或用 Rosetta。

---

## 坑 3：Seata 配置文件缺失

### 现象
`docker compose up` 报错找不到 `./deploy/docker/seata/registry.conf`

### 根因
`docker-compose.yml` 里挂载了该文件，但项目目录里没创建。

### 修复
创建 `deploy/docker/seata/registry.conf`，配置 Seata 注册到 Nacos：
```
registry {
  type = "nacos"
  nacos {
    application = "seata-server"
    serverAddr = "nacos:8848"
    namespace = ""
    group = "SEATA_GROUP"
    cluster = "default"
  }
}
config {
  type = "nacos"
  nacos {
    serverAddr = "nacos:8848"
    namespace = ""
    group = "SEATA_GROUP"
  }
}
```

---

## 坑 4：Dockerfile 依赖宿主机 target/ 目录

### 现象
```
lstat /target: no such file or directory
```

### 根因
原 Dockerfile 用 `COPY target/*.jar app.jar`，假设你已经在宿主机跑过 `mvn package`。但 Docker 构建时宿主机没有 `target/` 目录。

### 修复
重写 Dockerfile 为**真正的多阶段构建**——在 Docker 内部完成 Maven 编译：
```dockerfile
# 阶段1: 编译
FROM maven:3.9-eclipse-temurin-21 AS builder
ARG SERVICE_DIR
WORKDIR /build
COPY pom.xml ./
COPY services/*/pom.xml services/*/
RUN mvn dependency:go-offline -B -q
COPY services/ services/
RUN mvn package -pl ${SERVICE_DIR} -am -DskipTests -q

# 阶段2: 运行
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_DIR
COPY --from=builder /build/${SERVICE_DIR}/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
```

### 前端类比
旧方式 = 要求你本地先 `npm run build`，再把 `dist/` 拷进 Docker
新方式 = 在 Docker 内执行 `npm run build`，不依赖宿主机任何构建产物

---

## 坑 5：Docker 构建上下文看不到父 POM

### 现象
Maven 报错找不到父 POM 依赖

### 根因
原来每个微服务的 `build.context` 设为 `./services/xxx`，Dockerfile 只能看到子目录，看不到根目录的 `pom.xml`。

### 修复
改 `docker-compose.yml`，把 context 改为项目根目录，用 `SERVICE_DIR` 参数区分：
```yaml
gateway:
  build:
    context: .                              # ← 项目根目录
    dockerfile: deploy/docker/Dockerfile.java
    args:
      SERVICE_DIR: services/gateway          # ← 告诉 Dockerfile 编译哪个模块
```

---

## 坑 6：Maven 下载极慢（511+ 秒）

### 现象
```
[INFO] --- maven-dependency-plugin:3.6.1:go-offline (default-cli) ---
⠙ 511.4s
```

### 根因
从中国访问 Maven Central（海外服务器），带宽受限。

### 修复
创建 `deploy/docker/maven/settings.xml` 配置阿里云镜像，在 Dockerfile 中使用：
```dockerfile
COPY deploy/docker/maven/settings.xml /root/.m2/settings.xml
```

阿里云镜像 `https://maven.aliyun.com/repository/public` 速度可提升 10 倍+。

### 前端类比
相当于把 npm 的 registry 从 `npmjs.org` 切换到 `npmmirror.com`：
```bash
npm config set registry https://registry.npmmirror.com
```

---

## 坑 7：ARG 跨 FROM 丢失

### 现象
```
lstat /build/target: no such file or directory
```
注意路径是 `/build//target/`（中间多了一个空），说明 `SERVICE_DIR` 变空了。

### 根因
Docker 多阶段构建中，**`ARG` 在新的 `FROM` 之后会丢失**，必须重新声明。

### 修复
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
ARG SERVICE_DIR          # ← 阶段1 声明
...

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_DIR          # ← ⚠️ 必须再次声明！否则值为空
COPY --from=builder /build/${SERVICE_DIR}/target/*.jar app.jar
```

### 前端类比
类似 JavaScript 的作用域——在函数 A 里定义的变量，函数 B 里访问不到：
```js
function builder() {
  const SERVICE_DIR = 'services/gateway'  // 只在 builder 里可见
}
function runner() {
  console.log(SERVICE_DIR)  // ❌ undefined！必须重新声明
}
```

---

## 坑 8：缺失 Java 代码文件

### 现象
```
[ERROR] cannot find symbol: class ProductSpuMapper
```

### 根因
项目是半成品骨架，部分代码文件缺失：
- `ProductSpuMapper.java` — Controller 引用了但不存在
- `OrderMapper.java` — OrderService 引用了但不存在

### 修复
创建对应的 Mapper 接口：
```java
@Mapper
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {
}
```

---

## 坑 9：Redisson API 用错

### 现象
```
cannot find symbol: method tryLock(java.lang.String, int, int, TimeUnit)
location: variable redissonClient of type RedissonClient
```

### 根因
`RedissonClient` 没有 `tryLock` 方法，需要先获取 `RLock` 对象再调用。

### 修复
```java
// ❌ 错误
redissonClient.tryLock(lockKey, 0, 3, TimeUnit.SECONDS);
redissonClient.unlock(lockKey);

// ✅ 正确
RLock lock = redissonClient.getLock(lockKey);
lock.tryLock(0, 3, TimeUnit.SECONDS);
lock.unlock();
```

---

## 坑 10：微服务缺少 application.yml

### 现象
Spring Boot 启动报错或找不到配置

### 根因
User、Cart、Order、Payment 四个服务的 `src/main/resources/application.yml` 不存在。Spring Boot 必须有这个文件才能配置端口、数据库、Nacos 等。

### 修复
为每个服务创建 `application.yml`，配置：
- `server.port` — 服务端口
- `spring.datasource` — MySQL 连接
- `spring.data.redis` — Redis 连接
- `spring.cloud.nacos.discovery` — Nacos 注册

---

## 坑 11：Nacos 认证失败 "user not found"

### 现象
```
Caused by: NacosException: user not found!
```

### 根因
Nacos 2.3+ 默认开启了认证，微服务注册时没传用户名密码。

### 修复
每个微服务的 `application.yml` 里加 Nacos 认证：
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: nacos     # ← 必须加
        password: nacos     # ← 必须加
```

### 前端类比
类似请求 API 时没带 Authorization header，服务器返回 401。

---

## 坑 12：Gateway 缺少 spring.config.import

### 现象
```
No spring.config.import property has been defined
```

### 根因
Spring Cloud 2023 版本要求显式声明 Nacos 配置导入。

### 修复
```yaml
spring:
  config:
    import: "optional:nacos:"   # ← 注意必须加引号！
```

---

## 坑 13：YAML 的 optional:nacos: 冒号被误解析

### 现象
```
org.yaml.snakeyaml.scanner.ScannerException: mapping values are not allowed here
 in 'reader', line 8, column 27:
        import: optional:nacos:
```

### 根因
YAML 语法中，`nacos:` 末尾的冒号被解析器当成了新的键值对分隔符，而不是字符串的一部分。

### 修复
**必须加双引号**：
```yaml
# ❌ 错误 — YAML 把 nacos: 解析成新键
import: optional:nacos:

# ✅ 正确 — 引号包裹，YAML 当成字符串
import: "optional:nacos:"
```

### 前端类比
类似 JSON 里字符串值包含双引号需要转义：
```json
// ❌ 语法错误
{"text": He said "hello"}

// ✅ 转义
{"text": "He said \"hello\""}
```

> ⚠️ **YAML 教训**：值里包含 `:` `#` `{` `[` 等特殊字符时，一定要加引号！

---

## 坑 14：Redisson 给无密码 Redis 发 AUTH 命令

### 现象
```
ERR AUTH <password> called without any password configured for the default user.
```

### 根因
`spring.data.redis.password` 设为空字符串 `${REDIS_PASSWORD:}` 时，Redisson 仍然会发送 AUTH 命令（密码为空），而本地 Redis 没有设密码，拒绝了请求。

### 修复
完全移除 `password` 字段，让 Redisson 不发 AUTH：
```yaml
spring:
  data:
    redis:
      host: redis
      port: 6379
      # password: ${REDIS_PASSWORD:}   ← 删掉这行！
      redisson:
        config: |
          singleServerConfig:
            address: "redis://redis:6379"
            # 不配 password 字段 = 不发 AUTH
```

### 前端类比
类似连 MongoDB 时设了空密码，驱动还是会发 `authenticate` 命令，服务器拒绝。

---

## 坑 15：YAML 文件出现两个顶级 spring: 块

### 现象
Redisson 配置没生效，还是报 AUTH 错误

### 根因
YAML 文件里出现了两个 `spring:` 顶级块（第 3 行和第 45 行），**YAML 不会合并同名顶级块**，后者直接覆盖前者，导致第一个 `spring:` 里的配置全丢了。

```yaml
# ❌ 两个 spring: 块
spring:
  application:
    name: hc-user
  datasource: ...
  data:
    redis: ...

spring:                    # ← 第二个 spring: 覆盖了第一个！
  data:
    redis:
      redisson:
        config: ...
```

### 修复
合并为一个 `spring:` 块：
```yaml
# ✅ 合并成一个
spring:
  application:
    name: hc-user
  datasource: ...
  data:
    redis:
      host: redis
      redisson:
        config: |
          singleServerConfig:
            address: "redis://redis:6379"
```

### 前端类比
类似在 JavaScript 里两次 `const config = {}` 声明，后者覆盖前者：
```js
const config = { port: 7101, db: 'mysql' }  // 第一个
const config = { redis: 'redisson' }        // 第二个覆盖了！
// config.port → undefined 😱
```

---

## 坑 16：前端 Dockerfile 找不到 public/ 目录

### 现象
```
COPY --from=builder /app/public ./public
lstat /app/public: not found
```

### 根因
Next.js 项目的 `public/` 目录不存在（项目是空的），Dockerfile 尝试拷贝时报错。

### 修复
创建空目录 + 使用通配符：
```dockerfile
# ❌ 严格匹配，目录不存在就报错
COPY --from=builder /app/public ./public

# ✅ 通配符，不存在也能跳过
COPY --from=builder /app/public* ./public/
```
同时创建 `frontend/public/` 空目录。

---

## 坑 17：docker compose ps 看到 Restarting 不知道什么意思

### 现象
```
hc-gateway     Restarting (1) 1 second ago
```

### 解读
| STATUS | 含义 | 处理 |
|--------|------|------|
| `Up X minutes` | ✅ 正常运行 | 不用管 |
| `Restarting (N)` | ❌ 启动失败，反复重启 | 必须看日志 |
| `Exited (N)` | ❌ 已退出 | 必须看日志 |

因为 `docker-compose.yml` 配了 `restart: always`，启动失败后会不停重试。

### 排查套路
```bash
# 第1步：找谁在重启
docker compose ps

# 第2步：看报错
docker compose logs <服务名> --tail=30

# 第3步：修代码 → 重建
docker compose up -d --build <服务名>
```

---

## 坑 18：改了代码但没加 --build

### 现象
改了 `application.yml`，重启容器后还是报同样的错。

### 根因
`docker compose up -d gateway` 只是用**旧镜像**启动容器，不会重新构建。代码修改没打包进新镜像。

### 修复
```bash
# ❌ 不会重新构建
docker compose up -d gateway

# ✅ 强制重新构建镜像再启动
docker compose up -d --build gateway
```

### 前端类比
```bash
# ❌ 只重启 dev server，没重新 build
npm run dev

# ✅ 重新 build 后再启动
npm run build && npm run dev
```

---

## 坑 19：Nginx 启动时找不到 Gateway 域名

### 现象
```
nginx: [emerg] host not found in upstream "gateway:7100"
```

### 根因
Nginx 的 `upstream` 块在**启动时**就要解析域名，如果 Gateway 还没启动，Docker DNS 查不到 `gateway` 这个域名，Nginx 直接崩掉。

### 修复
去掉 `upstream` 块，改用 `resolver` + 变量，让 Nginx 在**收到请求时**才解析域名：
```nginx
# ❌ 旧方式 — 启动时就要解析，Gateway 还没起就崩
upstream gateway {
    server gateway:7100;
}

# ✅ 新方式 — 运行时才解析，Gateway 后起也没问题
resolver 127.0.0.11 valid=10s;  # Docker 内部 DNS
location /api/ {
    set $gateway_host gateway;
    proxy_pass http://$gateway_host:7100/;
}
```

### 前端类比
```js
// ❌ 旧方式 — import 时就 require，模块不存在就崩
const gateway = require('gateway')  // 如果 gateway 还没加载 → 崩

// ✅ 新方式 — 动态 import，用到时才加载
const gateway = await import('gateway')  // 模块还没就绪就等一下
```

---

## 坑 20：Sentinel 容器内端口和映射端口不一致

### 现象
`http://localhost:8070` 返回空内容或连接被拒

### 根因
`bladex/sentinel-dashboard:1.8.7` 镜像内部监听的端口是 **8858**，不是 8070。docker-compose.yml 写了 `"8070:8070"`，但容器内根本没有 8070 端口在监听。

### 修复
修正端口映射为 Mac 的 8070 → 容器内的 8858：
```yaml
# ❌ 错误 — 容器内没有 8070
ports:
  - "8070:8070"

# ✅ 正确 — Mac 访问 localhost:8070 → 转发到容器内 8858
ports:
  - "8070:8858"
```

同时微服务的 `SENTINEL_DASHBOARD` 环境变量也要改成容器内端口：
```yaml
SENTINEL_DASHBOARD: sentinel:8858   # 容器间互访用容器内端口
```

---

## 速查表：所有坑一览

| # | 坑 | 现象 | 关键字 | 修复 |
|---|------|------|--------|------|
| 1 | OrbStack 未启动 | `command not found: docker` | 符号链接 | `open -a OrbStack` |
| 2 | Nacos 无 ARM64 | `no matching manifest for arm64` | 平台 | `platform: linux/amd64` |
| 3 | Seata 配置缺失 | `file not found: registry.conf` | 文件 | 创建文件 |
| 4 | Dockerfile 依赖宿主机 | `lstat /target: not found` | 多阶段 | 重写为多阶段构建 |
| 5 | 构建上下文太小 | Maven 找不到父 POM | context | `context: .` |
| 6 | Maven 下载慢 | `511.4s` 卡住 | 镜像 | 阿里云 Maven 镜像 |
| 7 | ARG 跨 FROM 丢失 | `/build//target/` 空路径 | ARG | 第二个 FROM 后重新声明 |
| 8 | 缺 Mapper 文件 | `cannot find symbol` | 代码 | 创建 Mapper 接口 |
| 9 | Redisson API 错 | `method tryLock not found` | RLock | 先 getLock 再 tryLock |
| 10 | 缺 application.yml | Spring Boot 启动失败 | 配置 | 创建 yml |
| 11 | Nacos 认证失败 | `user not found!` | 认证 | 加 username/password |
| 12 | 缺 config.import | `No spring.config.import` | Nacos | 加 `spring.config.import` |
| 13 | YAML 冒号误解析 | `mapping values not allowed` | YAML | `"optional:nacos:"` 加引号 |
| 14 | Redisson 发空密码 | `AUTH called without password` | 密码 | 删除 password 字段 |
| 15 | YAML 双 spring: 块 | 配置丢失不生效 | YAML | 合并为一个块 |
| 16 | public/ 不存在 | `lstat /app/public: not found` | 前端 | 通配符 + 创建空目录 |
| 17 | Restarting 不理解 | `Restarting (1)` | 状态 | `docker compose logs` |
| 18 | 改代码没重建 | 还是旧代码 | 构建 | 加 `--build` |
| 19 | Nginx 找不到域名 | `host not found in upstream` | resolver | `resolver` + `set $var` 动态解析 |
| 20 | Sentinel 端口映射错 | `localhost:8070` 空响应 | 端口 | `"8070:8858"` (内部监听 8858) |

---

## 你现在的架构 = 单机版生产部署

```
用户浏览器
    │
    ▼
  Nginx (:80)          ← 反向代理 + 限流 (10r/s) + Gzip
    │
    ▼
  Gateway (:7100)       ← API 路由 + JWT 鉴权 + CORS
    │
    ├──→ hc-user (:7101)
    ├──→ hc-product (:7102)
    ├──→ hc-cart (:7103)
    ├──→ hc-order (:7104)
    └──→ hc-payment (:7105)
         │
         ▼
    ┌────────────────────┐
    │  MySQL  Redis  MQ  │   ← 数据存储 + 缓存 + 异步
    │  Nacos  Seata  ES  │   ← 注册中心 + 事务 + 搜索
    │  Sentinel          │   ← 限流降级
    └────────────────────┘
```

这和真正的生产环境架构一致，区别只是：

| 维度 | 你的 Mac | 生产环境 |
|------|---------|---------|
| 服务器 | 1 台 Mac | 10+ 台云服务器 |
| MySQL | Docker 容器 | 云数据库 RDS |
| Redis | Docker 容器 | 云 Redis |
| 部署方式 | `docker compose up` | Kubernetes / 云平台 |
| 配置管理 | application.yml 文件 | Nacos 配置中心 |
| 域名 | localhost | your-domain.com |
| HTTPS | 无 | 有 |
| CI/CD | 手动 build | GitHub Actions 自动 |

> 核心架构思想完全一样，只是基础设施从"自己管"变成"云厂商管"。

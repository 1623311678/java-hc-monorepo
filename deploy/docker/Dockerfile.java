# ===== Java 21 多阶段构建 =====
# 阶段1: Maven 编译打包（在 Docker 内完成，不依赖宿主机的 target/）
# 阶段2: 只拷贝 jar 到精简的 JRE 镜像运行
#
# 构建参数:
#   SERVICE_DIR — 微服务目录 (如 services/gateway)
#
# 用法:
#   docker compose build gateway
#   docker compose build hc-product

# ---------- 阶段1: 构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder

ARG SERVICE_DIR

WORKDIR /build

# 0) 配置阿里云 Maven 镜像（国内下载速度快 10 倍+）
COPY deploy/docker/maven/settings.xml /root/.m2/settings.xml

# 1) 先拷贝父 POM → 利用 Docker 层缓存，依赖不变则不重新下载
COPY pom.xml ./

# 2) 拷贝所有子模块的 pom.xml（Maven 需要 reactor 解析依赖）
COPY services/gateway/pom.xml services/gateway/pom.xml
COPY services/product/pom.xml services/product/pom.xml
COPY services/order/pom.xml services/order/pom.xml
COPY services/payment/pom.xml services/payment/pom.xml
COPY services/user/pom.xml services/user/pom.xml
COPY services/cart/pom.xml services/cart/pom.xml

# 3) 下载所有依赖（缓存层，POM 不变就不会重新下载）
#    使用阿里云镜像，速度从 10 分钟降到 1-2 分钟
RUN mvn dependency:go-offline -B -q 2>/dev/null || true

# 4) 拷贝全部源码
COPY services/ services/

# 5) 只编译目标模块（跳过测试）
RUN mvn package -pl ${SERVICE_DIR} -am -DskipTests -q

# ---------- 阶段2: 运行 ----------
FROM eclipse-temurin:21-jre-alpine

# ⚠️ 必须重新声明 ARG！Docker 多阶段构建中 ARG 不会跨 FROM 继承
ARG SERVICE_DIR

WORKDIR /app
COPY --from=builder /build/${SERVICE_DIR}/target/*.jar app.jar

ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV TZ=Asia/Shanghai

EXPOSE 7100
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

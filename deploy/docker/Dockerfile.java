# Java 21 多阶段构建
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 需要在 monorepo 根目录先 mvn package
COPY target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/app.jar app.jar

ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV TZ=Asia/Shanghai

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

# 电商高并发微服务 — Makefile

.PHONY: dev infra services frontend build up down logs ps clean reset-db backup

# ===== 一键全启动 =====
dev: infra
	@echo "⏳ 等待基础设施就绪 (15s)..."
	@sleep 15
	@$(MAKE) services
	@$(MAKE) frontend
	@echo "✅ 全部启动完成！"
	@echo "前端:      http://localhost"
	@echo "API网关:    http://localhost/api"
	@echo "Nacos:     http://localhost:8848/nacos (nacos/nacos)"
	@echo "Sentinel:  http://localhost:8070 (sentinel/sentinel)"
	@echo "RabbitMQ:  http://localhost:15672 (admin/admin123)"

# ===== 基础设施 (MySQL/Redis/Nacos/RabbitMQ/ES/Seata/Nginx) =====
infra:
	docker compose up -d mysql redis nacos rabbitmq elasticsearch seata sentinel nginx

# ===== 微服务 (先 Maven 构建) =====
services: build-java
	docker compose up -d gateway hc-product hc-order hc-payment hc-user hc-cart

# ===== 前端 =====
frontend:
	docker compose up -d frontend

# ===== Maven 构建 =====
build-java:
	cd services/gateway && mvn package -DskipBuild -q
	cd services/product && mvn package -DskipBuild -q
	cd services/order && mvn package -DskipBuild -q
	cd services/payment && mvn package -DskipBuild -q
	cd services/user && mvn package -DskipBuild -q
	cd services/cart && mvn package -DskipBuild -q

build:
	docker compose build

# ===== 控制 =====
up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f --tail=50

ps:
	docker compose ps

# ===== 数据库 =====
reset-db:
	docker compose down -v mysql-data
	docker compose up -d mysql
	@echo "⏳ 等待 MySQL 初始化 (10s)..."
	@sleep 10
	@echo "✅ 数据库已重置"

backup:
	docker compose exec mysql mysqldump -uroot -p$$MYSQL_PASSWORD --all-databases > backup_$$(date +%Y%m%d_%H%M%S).sql

# ===== Shell 进入容器 =====
shell-mysql:
	docker compose exec mysql mysql -uroot -p$$MYSQL_PASSWORD

shell-redis:
	docker compose exec redis redis-cli

shell-rabbit:
	docker compose exec rabbitmq rabbitmqctl list_queues

# ===== 清理 =====
clean:
	docker compose down -v
	docker system prune -f

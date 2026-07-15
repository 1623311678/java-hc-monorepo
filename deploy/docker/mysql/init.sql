-- 电商高并发微服务 — 数据库初始化
-- 每个服务独立库

-- ===== 商品库 =====
CREATE DATABASE IF NOT EXISTS hc_product DEFAULT CHARSET utf8mb4;
USE hc_product;

CREATE TABLE IF NOT EXISTS t_product_spu (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200)   NOT NULL COMMENT '商品名称',
    subtitle    VARCHAR(500)   NULL    COMMENT '副标题',
    category_id BIGINT         NULL    COMMENT '分类ID',
    brand_id    BIGINT         NULL    COMMENT '品牌ID',
    price       DECIMAL(10,2)  NOT NULL COMMENT '最低价',
    main_image  VARCHAR(500)   NULL    COMMENT '主图',
    images      JSON           NULL    COMMENT '图片列表',
    detail      TEXT           NULL    COMMENT '商品详情HTML',
    create_time DATETIME       DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT        DEFAULT 0,
    INDEX idx_category (category_id),
    INDEX idx_brand (brand_id)
) COMMENT='商品SPU';

CREATE TABLE IF NOT EXISTS t_product_sku (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    spu_id         BIGINT         NOT NULL COMMENT '关联SPU',
    sku_name       VARCHAR(300)   NOT NULL COMMENT 'SKU名称',
    spec_values    JSON           NULL    COMMENT '规格值',
    price          DECIMAL(10,2)  NOT NULL COMMENT '售价',
    original_price DECIMAL(10,2)  NULL    COMMENT '原价',
    stock          INT            NOT NULL DEFAULT 0 COMMENT '库存数',
    lock_stock     INT            NOT NULL DEFAULT 0 COMMENT '锁定库存',
    image          VARCHAR(500)   NULL    COMMENT 'SKU图片',
    create_time    DATETIME       DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT        DEFAULT 0,
    INDEX idx_spu (spu_id)
) COMMENT='商品SKU — 秒杀扣库存核心表';

-- ===== 订单库 =====
CREATE DATABASE IF NOT EXISTS hc_order DEFAULT CHARSET utf8mb4;
USE hc_order;

CREATE TABLE IF NOT EXISTS t_order (
    id             BIGINT        PRIMARY KEY COMMENT '雪花ID',
    user_id        BIGINT        NOT NULL,
    order_no       VARCHAR(64)   NOT NULL COMMENT '订单号',
    sku_id         BIGINT        NOT NULL,
    quantity       INT           NOT NULL DEFAULT 1,
    total_amount   DECIMAL(12,2) NOT NULL,
    pay_amount     DECIMAL(12,2) NULL,
    status         TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已发货 3已完成 4已取消 5已退款',
    pay_time       DATETIME      NULL,
    delivery_time  DATETIME      NULL,
    close_time     DATETIME      NULL,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) COMMENT='订单表';

-- ===== 用户库 =====
CREATE DATABASE IF NOT EXISTS hc_user DEFAULT CHARSET utf8mb4;
USE hc_user;

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)   NOT NULL,
    password    VARCHAR(200)  NOT NULL COMMENT 'BCrypt加密',
    phone       VARCHAR(20)   NULL,
    email       VARCHAR(100)  NULL,
    nickname    VARCHAR(50)   NULL,
    avatar      VARCHAR(500)  NULL,
    status      TINYINT       DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_username (username),
    UNIQUE INDEX uk_phone (phone)
) COMMENT='用户表';

-- ===== 支付库 =====
CREATE DATABASE IF NOT EXISTS hc_payment DEFAULT CHARSET utf8mb4;
USE hc_payment;

CREATE TABLE IF NOT EXISTS t_payment_record (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no       VARCHAR(64)   NOT NULL,
    user_id        BIGINT        NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    pay_type       TINYINT       NOT NULL COMMENT '1支付宝 2微信',
    pay_no         VARCHAR(100)  NULL    COMMENT '第三方支付流水号',
    status         TINYINT       DEFAULT 0 COMMENT '0待支付 1成功 2失败 3退款',
    callback_time  DATETIME      NULL,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order (order_no),
    INDEX idx_user (user_id)
) COMMENT='支付记录';

-- ===== 插入测试数据 =====
USE hc_product;
INSERT INTO t_product_spu (name, subtitle, price, main_image) VALUES
('iPhone 15 Pro', '钛金属设计 A17 Pro芯片', 7999.00, '/images/iphone15.jpg'),
('MacBook Pro 14', 'M3 Pro 18小时续航', 14999.00, '/images/macbook.jpg'),
('AirPods Pro 2', '自适应降噪 USB-C', 1899.00, '/images/airpods.jpg'),
('iPad Air', 'M2芯片 11英寸', 4799.00, '/images/ipad.jpg'),
('Apple Watch Ultra 2', '钛金属 双频GPS', 6499.00, '/images/watch.jpg');

INSERT INTO t_product_sku (spu_id, sku_name, spec_values, price, original_price, stock) VALUES
(1, 'iPhone 15 Pro 256G 钛金色', '{"颜色":"钛金色","容量":"256G"}', 7999.00, 8999.00, 100),
(1, 'iPhone 15 Pro 512G 蓝色钛金属', '{"颜色":"蓝色钛金属","容量":"512G"}', 9999.00, 10999.00, 50),
(2, 'MacBook Pro 14 M3 Pro 18G/512G', '{"芯片":"M3 Pro","内存":"18G","硬盘":"512G"}', 14999.00, 16999.00, 30),
(3, 'AirPods Pro 2 USB-C', '{"版本":"USB-C"}', 1899.00, 1999.00, 200),
(4, 'iPad Air M2 128G', '{"颜色":"星光色","容量":"128G"}', 4799.00, 4999.00, 80);

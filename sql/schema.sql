-- 抢票系统数据库建表脚本
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS ticket_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_system;

-- 1. 用户表
-- `user` 是 MySQL 保留字，必须使用反引号
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT NOT NULL COMMENT '用户ID(雪花ID)',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希(BCrypt)',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=正常',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 用户角色表
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(32) NOT NULL COMMENT '角色: ADMIN, USER',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- 3. 演出表
-- `show` 是 MySQL 保留字，必须使用反引号
CREATE TABLE IF NOT EXISTS `show` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '演出名称',
    description TEXT COMMENT '演出描述',
    category VARCHAR(64) DEFAULT NULL COMMENT '分类: 演唱会/话剧/体育等',
    poster_url VARCHAR(512) DEFAULT NULL COMMENT '海报URL',
    venue VARCHAR(256) DEFAULT NULL COMMENT '演出场馆',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=草稿, 1=已上架, 2=已下架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_create_time (status, create_time) COMMENT '用于演出列表按状态分页查询，避免 filesort'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出表';

-- 4. 演出场次表
CREATE TABLE IF NOT EXISTS show_session (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    show_id        BIGINT       NOT NULL COMMENT '关联演出ID',
    name           VARCHAR(128) NOT NULL COMMENT '场次名称',
    start_time     DATETIME     NOT NULL COMMENT '开始时间',
    end_time       DATETIME     NOT NULL COMMENT '结束时间',
    total_seats    INT          NOT NULL DEFAULT 0 COMMENT '总座位数',
    limit_per_user INT          NOT NULL DEFAULT 1 COMMENT '每用户限购数量',
    status         INT          NOT NULL DEFAULT 0 COMMENT '状态: 0=未开放, 1=销售中, 2=已结束, 3=已预热',
    row_count      INT          NOT NULL DEFAULT 0 COMMENT '座位网格行数',
    col_count      INT          NOT NULL DEFAULT 0 COMMENT '座位网格列数',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_show_id (show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出场次表';

-- 5. 座位表
CREATE TABLE IF NOT EXISTS seat (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    session_id   BIGINT      NOT NULL COMMENT '关联场次ID',
    row_no       INT         NOT NULL COMMENT '排号',
    col_no       INT         NOT NULL COMMENT '列号',
    type         INT         NOT NULL DEFAULT 1  COMMENT '座位类型: 1=普通, 2=情侣左, 3=情侣右',
    area_id      VARCHAR(32) NOT NULL DEFAULT '' COMMENT '价格区域ID，对应 seat_area.area_id',
    seat_name    VARCHAR(64)          DEFAULT NULL COMMENT '座位名称，如 1排01座',
    pair_seat_id BIGINT               DEFAULT NULL COMMENT '情侣连座配对座位ID，type=2/3时非空',
    status       INT         NOT NULL DEFAULT 0  COMMENT '状态: 0=可售, 1=已锁定, 2=已售',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位表';

-- 6. 订单表
-- `order` 是 MySQL 保留字，必须使用反引号
CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL COMMENT '订单编号(雪花ID)',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id BIGINT NOT NULL COMMENT '场次ID',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=待支付, 1=已支付, 2=已取消, 3=退款中, 4=已退款, 5=部分退款',
    pay_time DATETIME DEFAULT NULL COMMENT '支付时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间(下单后5分钟)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_user_status (user_id, status) COMMENT '用于用户订单列表查询',
    KEY idx_session_id (session_id) COMMENT '用于按场次查询订单',
    KEY idx_status_expire (status, expire_time) COMMENT '用于超时订单扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 7. 订单明细表
CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    seat_id BIGINT NOT NULL COMMENT '座位ID',
    price DECIMAL(10,2) NOT NULL COMMENT '成交价格',
    seat_info VARCHAR(128) DEFAULT NULL COMMENT '座位信息(如"A排5座")',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_seat_id (seat_id),
    KEY idx_order_seat (order_id, seat_id) COMMENT '用于订单明细关联查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 8. 支付表
CREATE TABLE IF NOT EXISTS payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    payment_no VARCHAR(64) NOT NULL COMMENT '支付流水号',
    channel VARCHAR(32) DEFAULT 'MOCK' COMMENT '支付渠道: MOCK, ALIPAY, WECHAT',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=处理中, 1=成功, 2=失败',
    trade_no VARCHAR(128) DEFAULT NULL COMMENT '第三方交易号',
    callback_time DATETIME DEFAULT NULL COMMENT '回调时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_id (order_id),
    KEY idx_order_status (order_id, status) COMMENT '用于按订单查询支付状态'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付表';

-- 9. 票据表
CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    seat_id BIGINT NOT NULL COMMENT '关联座位ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    qr_code VARCHAR(64) NOT NULL COMMENT '二维码内容(UUID)',
    ticket_no VARCHAR(16) NOT NULL COMMENT '票号(TK+6位随机)',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=未使用, 1=已使用, 2=已退款/作废',
    verify_time DATETIME DEFAULT NULL COMMENT '核验时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_qr_code (qr_code),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_order_id (order_id),
    KEY idx_seat_id (seat_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票据表';

-- 10. 座位价格区域表
CREATE TABLE IF NOT EXISTS seat_area (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL COMMENT '关联场次ID',
    area_id     VARCHAR(32)  NOT NULL COMMENT '区域标识(如 0、1，场次内唯一)',
    price       DECIMAL(10,2) NOT NULL COMMENT '区域售价',
    origin_price DECIMAL(10,2) NOT NULL COMMENT '区域原价',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_area (session_id, area_id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位价格区域表';


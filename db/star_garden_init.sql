-- =====================================================
-- 星芽花园 - MySQL 数据库初始化脚本
-- 学号：202339170206  姓名：魏佳冕
-- 使用方法：在 MySQL 中执行 source star_garden_init.sql
-- 或在命令行运行：mysql -u root -p < star_garden_init.sql
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS star_garden
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE star_garden;
SET NAMES utf8mb4;

-- 删除已存在的表（按外键依赖顺序）
DROP TABLE IF EXISTS friend_like;
DROP TABLE IF EXISTS achievement;
DROP TABLE IF EXISTS user_garden;
DROP TABLE IF EXISTS task_record;
DROP TABLE IF EXISTS plant;
DROP TABLE IF EXISTS users;

-- =====================================================
-- 1. 用户表
-- =====================================================
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(200),
    role VARCHAR(20) DEFAULT 'USER',
    enabled BOOLEAN DEFAULT TRUE,
    consecutive_days INT DEFAULT 0,
    total_plants INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 2. 植物库表
-- =====================================================
CREATE TABLE plant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    image_url VARCHAR(200),
    rarity VARCHAR(20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 3. 任务记录表
-- =====================================================
CREATE TABLE task_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    task_name VARCHAR(100),
    duration_minutes INT,
    completed_time DATETIME,
    plant_id BIGINT,
    planted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (plant_id) REFERENCES plant(id),
    INDEX idx_user_id (user_id),
    INDEX idx_completed_time (completed_time),
    INDEX idx_plant_id (plant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 4. 用户花园表
-- =====================================================
CREATE TABLE user_garden (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    plant_id BIGINT,
    position_x INT,
    position_y INT,
    plant_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (plant_id) REFERENCES plant(id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 5. 成就表
-- =====================================================
CREATE TABLE achievement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    achievement_type VARCHAR(50),
    achieved_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 6. 好友点赞表
-- =====================================================
CREATE TABLE friend_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT,
    to_user_id BIGINT,
    garden_view_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_user_id) REFERENCES users(id),
    FOREIGN KEY (to_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 7. 插入初始植物数据
-- =====================================================
INSERT INTO plant (id, name, description, rarity) VALUES
    (1, '智慧藤', '完成学习任务获得', 'COMMON'),
    (2, '专注花', '完成阅读任务获得', 'COMMON'),
    (3, '活力草', '完成运动任务获得', 'COMMON'),
    (4, '晨光花', '完成早起任务获得', 'RARE'),
    (5, '宁静叶', '完成冥想任务获得', 'COMMON'),
    (6, '星芽草', '默认植物', 'COMMON');

-- =====================================================
-- 8. 插入管理员账号
-- 用户名：admin  密码：admin123（将在安全加固中改为 BCrypt）
-- =====================================================
INSERT INTO users (id, username, email, password, nickname, role, enabled, create_time)
VALUES (1, 'admin', 'admin@star-garden.com', 'admin123', '管理员', 'ADMIN', TRUE, NOW());

-- =====================================================
-- 9. 验证数据
-- =====================================================
SELECT '用户表' AS 表名, COUNT(*) AS 记录数 FROM users
UNION ALL
SELECT '植物表', COUNT(*) FROM plant
UNION ALL
SELECT '任务记录表', COUNT(*) FROM task_record
UNION ALL
SELECT '用户花园表', COUNT(*) FROM user_garden
UNION ALL
SELECT '成就表', COUNT(*) FROM achievement
UNION ALL
SELECT '好友点赞表', COUNT(*) FROM friend_like;

-- =====================================================
-- 初始化完成
-- =====================================================

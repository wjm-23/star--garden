-- =====================================================
-- 星芽花园 - 数据库初始化脚本
-- 学号：202339170206  姓名：魏佳冕
-- =====================================================

-- 删除已存在的表（按依赖顺序）
DROP TABLE IF EXISTS TASK_RECORD;
DROP TABLE IF EXISTS USER_GARDEN;
DROP TABLE IF EXISTS ACHIEVEMENT;
DROP TABLE IF EXISTS FRIEND_LIKE;
DROP TABLE IF EXISTS PLANT;
DROP TABLE IF EXISTS USERS;

-- =====================================================
-- 1. 用户表
-- =====================================================
CREATE TABLE USERS (
                       ID BIGINT PRIMARY KEY AUTO_INCREMENT,
                       USERNAME VARCHAR(50) NOT NULL UNIQUE,
                       EMAIL VARCHAR(100) NOT NULL UNIQUE,
                       PASSWORD VARCHAR(100) NOT NULL,
                       NICKNAME VARCHAR(50),
                       AVATAR_URL VARCHAR(200),
                       ROLE VARCHAR(20) DEFAULT 'USER',
                       ENABLED BOOLEAN DEFAULT TRUE,
                       CONSECUTIVE_DAYS INT DEFAULT 0,
                       TOTAL_PLANTS INT DEFAULT 0,
                       CREATE_TIME TIMESTAMP
);

-- =====================================================
-- 2. 植物库表
-- =====================================================
CREATE TABLE PLANT (
                       ID BIGINT PRIMARY KEY AUTO_INCREMENT,
                       NAME VARCHAR(50) NOT NULL,
                       DESCRIPTION VARCHAR(200),
                       IMAGE_URL VARCHAR(200),
                       RARITY VARCHAR(20)
);

-- =====================================================
-- 3. 任务记录表
-- =====================================================
CREATE TABLE TASK_RECORD (
                             ID BIGINT PRIMARY KEY AUTO_INCREMENT,
                             USER_ID BIGINT,
                             TASK_NAME VARCHAR(100),
                             DURATION_MINUTES INT,
                             COMPLETED_TIME TIMESTAMP,
                             PLANT_ID BIGINT,
                             PLANTED BOOLEAN DEFAULT FALSE,
                             FOREIGN KEY (USER_ID) REFERENCES USERS(ID),
                             FOREIGN KEY (PLANT_ID) REFERENCES PLANT(ID)
);

-- =====================================================
-- 4. 用户花园表
-- =====================================================
CREATE TABLE USER_GARDEN (
                             ID BIGINT PRIMARY KEY AUTO_INCREMENT,
                             USER_ID BIGINT,
                             PLANT_ID BIGINT,
                             POSITION_X INT,
                             POSITION_Y INT,
                             PLANT_TIME TIMESTAMP,
                             FOREIGN KEY (USER_ID) REFERENCES USERS(ID),
                             FOREIGN KEY (PLANT_ID) REFERENCES PLANT(ID)
);

-- =====================================================
-- 5. 成就表
-- =====================================================
CREATE TABLE ACHIEVEMENT (
                             ID BIGINT PRIMARY KEY AUTO_INCREMENT,
                             USER_ID BIGINT,
                             ACHIEVEMENT_TYPE VARCHAR(50),
                             ACHIEVED_TIME TIMESTAMP,
                             FOREIGN KEY (USER_ID) REFERENCES USERS(ID)
);

-- =====================================================
-- 6. 好友点赞表
-- =====================================================
CREATE TABLE FRIEND_LIKE (
                             ID BIGINT PRIMARY KEY AUTO_INCREMENT,
                             FROM_USER_ID BIGINT,
                             TO_USER_ID BIGINT,
                             GARDEN_VIEW_TIME TIMESTAMP,
                             FOREIGN KEY (FROM_USER_ID) REFERENCES USERS(ID),
                             FOREIGN KEY (TO_USER_ID) REFERENCES USERS(ID)
);

-- =====================================================
-- 7. 插入初始植物数据
-- =====================================================
INSERT INTO PLANT (ID, NAME, DESCRIPTION, RARITY) VALUES
                                                      (1, '智慧藤', '完成学习任务获得', 'COMMON'),
                                                      (2, '专注花', '完成阅读任务获得', 'COMMON'),
                                                      (3, '活力草', '完成运动任务获得', 'COMMON'),
                                                      (4, '晨光花', '完成早起任务获得', 'RARE'),
                                                      (5, '宁静叶', '完成冥想任务获得', 'COMMON'),
                                                      (6, '星芽草', '默认植物', 'COMMON');

-- =====================================================
-- 8. 插入管理员账号
-- 用户名：admin  密码：admin123
-- =====================================================
INSERT INTO USERS (ID, USERNAME, EMAIL, PASSWORD, ROLE, ENABLED, CREATE_TIME)
VALUES (1, 'admin', 'admin@star-garden.com', 'admin123', 'ADMIN', TRUE, NOW());

-- =====================================================
-- 9. 验证数据
-- =====================================================
SELECT '用户表' AS 表名, COUNT(*) AS 记录数 FROM USERS
UNION ALL
SELECT '植物表', COUNT(*) FROM PLANT
UNION ALL
SELECT '任务记录表', COUNT(*) FROM TASK_RECORD
UNION ALL
SELECT '用户花园表', COUNT(*) FROM USER_GARDEN
UNION ALL
SELECT '成就表', COUNT(*) FROM ACHIEVEMENT
UNION ALL
SELECT '好友点赞表', COUNT(*) FROM FRIEND_LIKE;

-- =====================================================
-- 初始化完成
-- =====================================================
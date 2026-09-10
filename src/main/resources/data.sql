-- =====================================================
-- 星芽花园 - 初始化数据 (data.sql)
-- Spring Boot 启动时自动执行
-- 学号：202339170206  姓名：魏佳冕
-- =====================================================

-- 插入植物数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO plant (id, name, description, image_url, rarity) VALUES
    (1, '智慧藤', '完成学习任务获得', NULL, 'COMMON'),
    (2, '专注花', '完成阅读任务获得', NULL, 'COMMON'),
    (3, '活力草', '完成运动任务获得', NULL, 'COMMON'),
    (4, '晨光花', '完成早起任务获得', NULL, 'RARE'),
    (5, '宁静叶', '完成冥想任务获得', NULL, 'COMMON'),
    (6, '星芽草', '默认植物', NULL, 'COMMON');

-- 插入管理员账号（使用 INSERT IGNORE 避免重复插入）
-- 注意：密码将在第4步安全加固中改为 BCrypt 加密
INSERT IGNORE INTO users (id, username, email, password, nickname, role, enabled, consecutive_days, total_plants, create_time) VALUES
    (1, 'admin', 'admin@star-garden.com', 'admin123', '管理员', 'ADMIN', TRUE, 0, 0, NOW());

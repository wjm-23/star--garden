package com.stargarden.service;

import com.stargarden.dto.RecommendationItem;
import com.stargarden.entity.Plant;
import com.stargarden.entity.TaskRecord;
import com.stargarden.entity.User;
import com.stargarden.entity.UserGarden;
import com.stargarden.repository.AchievementRepository;
import com.stargarden.repository.PlantRepository;
import com.stargarden.repository.TaskRecordRepository;
import com.stargarden.repository.UserGardenRepository;
import com.stargarden.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心业务集成测试（H2 内存库）：
 * 1. 完成任务 → 写入 task_record + 掉落植物 + 累计种植数/成就
 * 2. 花园种植 → 待种队列消耗、重复位置拦截
 * 3. 植物生长阶段 → 花园地图返回 stage
 * 4. 混合推荐 → 返回 Top-N 真实可执行任务
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class GardenServiceIntegrationTest {

    @Autowired private GardenService gardenService;
    @Autowired private RecommendationService recommendationService;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRecordRepository taskRecordRepository;
    @Autowired private UserGardenRepository userGardenRepository;
    @Autowired private PlantRepository plantRepository;
    @Autowired private AchievementRepository achievementRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        taskRecordRepository.deleteAll();
        userGardenRepository.deleteAll();
        achievementRepository.deleteAll();

        // DataSeeder 是 CommandLineRunner，测试上下文不会执行；
        // 这里显式播种 plant 表（id 与 PlantTaskType 的 plantId 映射一致）
        if (plantRepository.count() == 0) {
            String[][] plants = {
                    {"1", "智慧藤", "COMMON"}, {"2", "专注花", "COMMON"}, {"3", "活力草", "COMMON"},
                    {"4", "晨光花", "RARE"}, {"5", "宁静叶", "COMMON"}, {"6", "星芽草", "COMMON"}
            };
            for (String[] p : plants) {
                jdbcTemplate.update("INSERT INTO plant (id, name, rarity) VALUES (?, ?, ?)",
                        Long.parseLong(p[0]), p[1], p[2]);
            }
        }

        user = new User();
        user.setUsername("tester_" + System.nanoTime());
        user.setNickname("测试用户");
        user.setPassword("x");
        user.setTotalPlants(0);
        user.setConsecutiveDays(0);
        user.setGardenSize(6);
        user = userRepository.save(user);
    }

    @Test
    @DisplayName("完成任务：写入专注记录、推导类别、掉落植物、累计种植数 +1")
    void completeTaskWritesRecordAndRewardsPlant() {
        Plant reward = gardenService.completeTaskAndGetPlant(user, "考研英语单词", 45);

        assertNotNull(reward);
        List<TaskRecord> records = taskRecordRepository.findByUserId(user.getId());
        assertEquals(1, records.size());
        TaskRecord r = records.get(0);
        assertEquals("考研英语单词", r.getTaskName());
        assertEquals("学习", r.getTaskCategory());   // 类别由关键词推导
        assertEquals(45, r.getDurationMinutes());
        assertFalse(r.isPlanted());                  // 进入待种背包，未自动种植
        assertEquals(reward.getId(), r.getPlantId());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(1, reloaded.getTotalPlants());
        // 首次种植成就解锁
        assertTrue(achievementRepository.existsByUserIdAndAchievementType(user.getId(), "FIRST_PLANT"));
    }

    @Test
    @DisplayName("种植：消耗待种种子、占据格子；同一格重复种植被拦截")
    void plantToGardenConsumesSeedAndRejectsOccupiedCell() {
        Plant reward = gardenService.completeTaskAndGetPlant(user, "晨跑", 30);

        // 待种背包中应有 1 颗
        List<Map<String, Object>> pending = gardenService.getPendingPlants(user);
        assertEquals(1, pending.size());
        assertEquals(1, pending.get(0).get("count"));

        UserGarden planted = gardenService.plantToGarden(user, reward.getId(), 2, 3);
        assertNotNull(planted.getId());
        assertEquals(2, planted.getPositionX());
        assertEquals(3, planted.getPositionY());

        // 种植后待种背包清空，记录标记为已种植
        assertTrue(gardenService.getPendingPlants(user).isEmpty());
        assertTrue(taskRecordRepository.findByUserId(user.getId()).get(0).isPlanted());

        // 同格重复种植应抛异常
        assertThrows(RuntimeException.class,
                () -> gardenService.plantToGarden(user, reward.getId(), 2, 3));
    }

    @Test
    @DisplayName("花园地图返回生长阶段：同一植物持续专注，发芽→成长→开花")
    void gardenMapExposesGrowthStage() {
        // 种下「深度学习」奖励的植物 → 刚种下无后续同类专注 → 发芽
        Plant p1 = gardenService.completeTaskAndGetPlant(user, "深度学习", 20);
        gardenService.plantToGarden(user, p1.getId(), 0, 0);

        Map<String, Object> map0 = gardenService.getUserGardenMap(user);
        Map<String, Object> cell0 = (Map<String, Object>) map0.get("0,0");
        assertEquals(0, cell0.get("stage"));
        assertEquals("发芽", cell0.get("stageName"));

        // 继续完成同一植物（同一任务名→同一奖励植物）的专注，累计超过阈值 → 成长
        gardenService.completeTaskAndGetPlant(user, "深度学习", GardenService.MINUTES_GROW);
        Map<String, Object> map1 = gardenService.getUserGardenMap(user);
        Map<String, Object> cell1 = (Map<String, Object>) map1.get("0,0");
        assertEquals(1, cell1.get("stage"));
        assertEquals("成长", cell1.get("stageName"));

        // 同类专注继续累计到开花阈值 → 开花
        gardenService.completeTaskAndGetPlant(user, "深度学习", GardenService.MINUTES_BLOOM);
        Map<String, Object> map2 = gardenService.getUserGardenMap(user);
        Map<String, Object> cell2 = (Map<String, Object>) map2.get("0,0");
        assertEquals(2, cell2.get("stage"));
        assertEquals("开花", cell2.get("stageName"));
    }

    @Test
    @DisplayName("不同植物之间经验互不相通：种下 A 后专注 B，A 仍停留在发芽")
    void growthExperienceIsPerPlant() {
        Plant plantA = gardenService.completeTaskAndGetPlant(user, "深度学习", 20);
        gardenService.plantToGarden(user, plantA.getId(), 3, 3);

        // 专注另一株植物对应的任务，不应给 A 增加经验
        gardenService.completeTaskAndGetPlant(user, "晨跑", 120);

        Map<String, Object> cell = (Map<String, Object>) gardenService.getUserGardenMap(user).get("3,3");
        assertEquals(0, cell.get("stage"));
        assertEquals("发芽", cell.get("stageName"));
    }

    @Test
    @DisplayName("铲除植物：格子腾出后可重新种植")
    void removePlantFreesCell() {
        Plant reward = gardenService.completeTaskAndGetPlant(user, "课外阅读", 30);
        gardenService.plantToGarden(user, reward.getId(), 1, 1);

        gardenService.removePlant(user, 1, 1);
        assertNull(userGardenRepository.findByUserIdAndPositionXAndPositionY(user.getId(), 1, 1));
        assertTrue(gardenService.getUserGardenMap(user).isEmpty());

        // 铲除后格子可再次使用（再完成一次任务获得新种子）
        Plant reward2 = gardenService.completeTaskAndGetPlant(user, "课外阅读", 30);
        assertNotNull(gardenService.plantToGarden(user, reward2.getId(), 1, 1));
    }

    @Test
    @DisplayName("混合推荐：冷启动用户也能返回 Top-N 可执行任务")
    void recommendReturnsExecutableTasks() {
        // 行为记录不足 5 条 → 走冷启动分支（全局热度 + 标签 + 时段）
        gardenService.completeTaskAndGetPlant(user, "深度学习", 45);

        List<RecommendationItem> items = recommendationService.recommend(user.getId(), 3);
        assertFalse(items.isEmpty());
        assertTrue(items.size() <= 3);
        for (RecommendationItem it : items) {
            assertNotNull(it.getTaskName(), "推荐必须落地为具体任务名");
            assertNotNull(it.getCategory());
            assertNotNull(it.getReason(), "每条推荐需附可解释理由");
            assertTrue(it.getScore() >= 0);
        }
        // 得分降序
        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).getScore() >= items.get(i).getScore());
        }
    }

    @Test
    @DisplayName("混合推荐：行为充足用户走 UserCF 分支且结果按类别去重")
    void recommendWithEnoughHistoryUsesCollaborativeBranch() {
        String[] tasks = {"深度学习", "考研英语单词", "晨跑", "睡前冥想", "项目开发", "课外阅读"};
        for (int i = 0; i < tasks.length; i++) {
            gardenService.completeTaskAndGetPlant(user, tasks[i], 30 + i * 5);
        }

        List<RecommendationItem> items = recommendationService.recommend(user.getId(), 5);
        // 当天已完成的类别会被过滤，因此结果数 ≤ 5，但不应为空
        assertFalse(items.isEmpty());
        assertTrue(items.size() <= 5);
        long distinctCategories = items.stream().map(RecommendationItem::getCategory).distinct().count();
        assertEquals(items.size(), distinctCategories, "同一类别不应重复推荐");
    }

    @Test
    @DisplayName("花园扩建：边长 +1，上限 10×10")
    void expandGardenUpToLimit() {
        int newSize = gardenService.expandGarden(user);
        assertEquals(7, newSize);

        user.setGardenSize(10);
        assertThrows(RuntimeException.class, () -> gardenService.expandGarden(user));
    }
}
